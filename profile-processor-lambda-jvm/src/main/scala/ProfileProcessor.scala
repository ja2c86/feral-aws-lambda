import cats._
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import cats.effect.std.{CountDownLatch, _}
import com.example.model.S3Event
import feral.lambda._
import fs2.{Stream, text}
import model._
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, PutItemRequest, PutItemResponse}
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse, PutObjectRequest, PutObjectResponse}
import software.amazon.awssdk.services.ses.SesAsyncClient
import software.amazon.awssdk.services.ses.model.{Body, Content, Destination, Message, SendEmailRequest, SendEmailResponse}

import java.net.URI
import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

class ProfileProcessor extends IOLambda.Simple[S3Event, INothing] {

  type Init = ProfileProcessorConfig
  override def init: Resource[IO, Init] = Resource.eval(initConfig[IO])

  private def initConfig[F[_] : Applicative]: F[ProfileProcessorConfig] = {
    Applicative[F].pure {
      ProfileProcessorConfig(
        s"http://${sys.env("LOCALSTACK_HOSTNAME")}:4566",
        sys.env("REGION"),
        Credentials(sys.env("ACCESS_KEY"), sys.env("SECRET_KEY")),
        sys.env("DYNAMODB_TABLE"),
        sys.env("NEW_BUCKET_NAME"),
        sys.env("ARCHIVED_BUCKET_NAME"),
        sys.env("SES_SOURCE_ADDRESS"),
        sys.env("TOTAL_WORKERS").toInt
      )
    }
  }

  override def apply(event: S3Event, context: Context[IO], init: Init): IO[None.type] = {
    handleRequest[IO](event, init).as(None)
  }

  private def handleRequest[F[_] : Console : Async](event: S3Event, config: ProfileProcessorConfig): F[Unit] = {
    for {
      queue <- Queue.unbounded[F, Profile]
      latch <- CountDownLatch[F](config.totalWorkers)
      _ <- Console[F].println(s"Received event: $event")
      _ <- event.records.traverse(record => readFile(config, queue, record.s3.bucket.name, record.s3.`object`.key))
      totalNew <- queue.size
      _ <- Console[F].println(s"$totalNew new profiles received")
      _ <- List.range(0, config.totalWorkers).parTraverse(workerId => processProfile(config, workerId, queue, latch))
      _ <- latch.await
      _ <- Console[F].println(s"Process completed")
    } yield ()
  }

  private def readFile[F[_] : Console : Async](config: ProfileProcessorConfig, queue: Queue[F, Profile], bucketName: String, objectKey: String): F[Unit] = {
    def getS3Object[G[_] : Async](client: S3AsyncClient, bucketName: String, objectKey: String): G[ResponseBytes[GetObjectResponse]] = {
      val request = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(objectKey)
        .build()

      Async[G].fromFuture {
        Async[G].pure(client.getObject(request, AsyncResponseTransformer.toBytes[GetObjectResponse]).asScala)
      }
    }

    def responseToByteArray[G[_] : Applicative](response: ResponseBytes[GetObjectResponse]): G[Array[Byte]] = {
      Applicative[G].pure(response.asByteArray())
    }

    def parseProfile[G[_] : Applicative](line: String): G[Option[Profile]] = {
      val result = line.split("\\|").map(_.trim).toList match {
        case firstName :: lastName :: email :: address :: phoneNumber :: age :: Nil =>
          val id = UUID.randomUUID().toString
          Some(Profile(id, firstName, lastName, email, address, phoneNumber, age.toInt))

        case _ => None
      }

      Applicative[G].pure(result)
    }

    for {
      client <- buildS3Client[F](config)
      response <- getS3Object[F](client, bucketName, objectKey)
      byteArray <- responseToByteArray[F](response)
      _ <-
        Stream.emits(byteArray)
          .through(text.utf8.decode)
          .through(text.lines)
          .evalMap(line => parseProfile[F](line))
          .collect { case Some(profile) => profile }
          .evalMap(queue.offer)
          .compile
          .drain
      _ <- Console[F].println(s"$bucketName/$objectKey content loaded successfully.")
    } yield ()
  }

  private def processProfile[F[_] : Console : Async](config: ProfileProcessorConfig, workerId: Int, queue: Queue[F, Profile], latch: CountDownLatch[F]): F[Unit] = {
    for {
      pending <- queue.size
      _ <-
        pending match {
          case 0 =>
            latch.release >> Console[F].println(s"Worker $workerId finalized")
          case _ =>
            for {
              profile <- queue.take
              _ <- registerProfile(config, profile)
              _ <- archiveProfile(config, profile)
              _ <- sendEmail(config, profile)
              _ <- processProfile(config, workerId, queue, latch)
            } yield ()
        }
    } yield ()
  }

  private def registerProfile[F[_] : Console : Async](config: ProfileProcessorConfig, profile: Profile): F[Unit] = {
    def putDynamoDBItem[G[_] : Console : Async](client: DynamoDbAsyncClient, config: ProfileProcessorConfig, profile: Profile): G[PutItemResponse] = {
      val item = Map(
        "id" -> AttributeValue.fromS(profile.id),
        "firstName" -> AttributeValue.fromS(profile.firstName),
        "lastName" -> AttributeValue.fromS(profile.lastName),
        "email" -> AttributeValue.fromS(profile.email),
        "address" -> AttributeValue.fromS(profile.address),
        "phoneNumber" -> AttributeValue.fromS(profile.phoneNumber),
        "age" -> AttributeValue.fromN(profile.age.toString),
        "timestamp" -> AttributeValue.fromN(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toString)
      )

      val request = PutItemRequest.builder
        .tableName(config.tableName)
        .item(item.asJava)
        .build()

      Async[G].fromFuture {
        Async[G].pure(client.putItem(request).asScala)
      }
    }

    for {
      client <- buildDynamoDBClient[F](config)
      _ <- putDynamoDBItem[F](client, config, profile)
      _ <- Console[F].println(s"${profile.id} registered successfully.")
    } yield ()
  }

  private def archiveProfile[F[_] : Console : Async](config: ProfileProcessorConfig, profile: Profile): F[Unit] = {
    def putS3Object[G[_] : Async](client: S3AsyncClient, config: ProfileProcessorConfig, profile: Profile): G[PutObjectResponse] = {
      val request = PutObjectRequest.builder()
        .bucket(config.archivedBucketName)
        .key(s"${profile.id}.txt")
        .build()

      Async[G].fromFuture {
        Async[G].pure(client.putObject(request, AsyncRequestBody.fromBytes(profile.toString.getBytes)).asScala)
      }
    }

    for {
      client <- buildS3Client[F](config)
      _ <- putS3Object[F](client, config, profile)
      _ <- Console[F].println(s"${profile.id} archived successfully.")
    } yield ()
  }

  private def sendEmail[F[_]: Console : Async](config: ProfileProcessorConfig, profile: Profile): F[Unit] = {
    def sendSESEmail[G[_]: Console : Async](client: SesAsyncClient, profile: Profile): G[SendEmailResponse] = {
      val request = SendEmailRequest.builder()
        .destination(
          Destination.builder()
            .toAddresses(profile.email)
            .build()
        )
        .message(
          Message.builder()
            .body(
              Body.builder()
                .text(
                  Content.builder()
                    .data(s"Hi ${profile.firstName} ${profile.lastName}, your profile was uploaded successfully in our system")
                    .build()
                )
                .build()
            )
            .subject(
              Content.builder()
                .data("Profile Uploaded Successfully")
                .build()
            )
            .build()
        )
        .source(config.sourceAddress)
        .build()

      Async[G].fromFuture {
        Async[G].pure(client.sendEmail(request).asScala)
      }
    }

    for {
      client <- buildSESClient[F](config)
      _ <- sendSESEmail[F](client, profile)
      _ <- Console[F].println(s"${profile.id} email sent successfully.")
    } yield ()
  }

  private def buildDynamoDBClient[F[_] : Applicative](config: ProfileProcessorConfig): F[DynamoDbAsyncClient] = {
    val dynamoDBClient = DynamoDbAsyncClient.builder()
      .region(Region.of(config.region))
      .endpointOverride(new URI(config.endpoint))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(config.credentials.accessKeyId, config.credentials.secretAccessKey)
        )
      )
      .build()

    Applicative[F].pure(dynamoDBClient)
  }

  private def buildS3Client[F[_] : Applicative](config: ProfileProcessorConfig): F[S3AsyncClient] = {
    val s3Client = S3AsyncClient.builder()
      .region(Region.of(config.region))
      .endpointOverride(new URI(config.endpoint))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(config.credentials.accessKeyId, config.credentials.secretAccessKey)
        )
      )
      .build()

    Applicative[F].pure(s3Client)
  }

  private def buildSESClient[F[_] : Applicative](config: ProfileProcessorConfig): F[SesAsyncClient] = {
    val sesClient = SesAsyncClient.builder()
      .region(Region.of(config.region))
      .endpointOverride(new URI(config.endpoint))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(config.credentials.accessKeyId, config.credentials.secretAccessKey)
        )
      )
      .build()

    Applicative[F].pure(sesClient)
  }
}
