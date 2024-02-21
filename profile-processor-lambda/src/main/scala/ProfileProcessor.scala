import cats._
import cats.implicits._
import cats.effect._
import cats.effect.implicits._
import cats.effect.std.{CountDownLatch, _}
import com.example.model.S3Event
import facade.UUID
import facade.amazonaws.{AWSConfig, AWSCredentials}
import facade.amazonaws.services.dynamodb.{AttributeValue, DynamoDB, PutItemInput, PutItemInputAttributeMap, PutItemOutput}
import facade.amazonaws.services.s3.{GetObjectOutput, GetObjectRequest, PutObjectOutput, PutObjectRequest, S3}
import facade.amazonaws.services.ses.{Body, Content, Destination, Message, SES, SendEmailRequest, SendEmailResponse}
import feral.lambda._
import fs2.{Stream, text}
import model._

import java.time.{LocalDateTime, ZoneOffset}

import scala.scalajs.js.{Dictionary, Dynamic, Array => JsArray}
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}

object ProfileProcessor extends IOLambda.Simple[S3Event, INothing] {

  type Init = ProfileProcessorConfig
  override def init: Resource[IO, Init] = Resource.eval(initConfig[IO])

  private def initConfig[F[_] : Applicative]: F[ProfileProcessorConfig] = {
    val env: Dynamic = Dynamic.global.process.env

    Applicative[F].pure {
      ProfileProcessorConfig(
        s"http://${env.LOCALSTACK_HOSTNAME.asInstanceOf[String]}:4566",
        env.REGION.asInstanceOf[String],
        Credentials(env.ACCESS_KEY.asInstanceOf[String], env.SECRET_KEY.asInstanceOf[String]),
        env.DYNAMODB_TABLE.asInstanceOf[String],
        env.NEW_BUCKET_NAME.asInstanceOf[String],
        env.ARCHIVED_BUCKET_NAME.asInstanceOf[String],
        env.SES_SOURCE_ADDRESS.asInstanceOf[String],
        env.TOTAL_WORKERS.asInstanceOf[Int]
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
    def getS3Object[G[_] : Async](client: S3, bucketName: String, objectKey: String): G[GetObjectOutput] = {
      val request = GetObjectRequest(
        Bucket = bucketName,
        Key = objectKey
      )

      Async[G].fromFuture {
        Async[G].pure(client.getObjectFuture(request))
      }
    }

    def responseToByteArray[G[_] : Applicative](response: GetObjectOutput): G[Array[Byte]] = {
      val arrayBuffer = response.Body.asInstanceOf[ArrayBuffer]
      val uInt8Array = new Uint8Array(arrayBuffer)
      Applicative[G].pure(uInt8Array.toArray.asInstanceOf[Array[Byte]])
    }

    def parseProfile[G[_] : Applicative](line: String): G[Option[Profile]] = {
      val result = line.split("\\|").map(_.trim).toList match {
        case firstName :: lastName :: email :: address :: phoneNumber :: age :: Nil =>
          val id = UUID.v4().toOption.getOrElse("")
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
    def putDynamoDBItem[G[_] : Async](client: DynamoDB, config: ProfileProcessorConfig, profile: Profile): G[PutItemOutput] = {
      val item: PutItemInputAttributeMap = Dictionary(
        ("id", AttributeValue.S(profile.id)),
        ("firstName", AttributeValue.S(profile.firstName)),
        ("lastName", AttributeValue.S(profile.lastName)),
        ("email", AttributeValue.S(profile.email)),
        ("address", AttributeValue.S(profile.address)),
        ("phoneNumber", AttributeValue.S(profile.phoneNumber)),
        ("age", AttributeValue.NFromInt(profile.age)),
        ("timestamp", AttributeValue.NFromDouble(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)))
      )

      Async[G].fromFuture {
        Async[G].pure(client.putItemFuture(PutItemInput(item, config.tableName)))
      }
    }

    for {
      client <- buildDynamoDBClient[F](config)
      _ <- putDynamoDBItem[F](client, config, profile)
      _ <- Console[F].println(s"${profile.id} registered successfully.")
    } yield ()
  }

  private def archiveProfile[F[_] : Console : Async](config: ProfileProcessorConfig, profile: Profile): F[Unit] = {
    def putS3Object[G[_] : Async](client: S3, config: ProfileProcessorConfig, profile: Profile): G[PutObjectOutput] = {
      val request = PutObjectRequest(
        Bucket = config.archivedBucketName,
        Key = s"${profile.id}.txt",
        Body = profile.toString
      )

      Async[G].fromFuture {
        Async[G].pure(client.putObjectFuture(request))
      }
    }

    for {
      client <- buildS3Client[F](config)
      _ <- putS3Object[F](client, config, profile)
      _ <- Console[F].println(s"${profile.id} archived successfully.")
    } yield ()
  }

  private def sendEmail[F[_]: Console : Async](config: ProfileProcessorConfig, profile: Profile): F[Unit] = {
    def sendSESEmail[G[_]: Console : Async](client: SES, profile: Profile): G[SendEmailResponse] = {
      val request = SendEmailRequest(
        Destination = Destination(
          ToAddresses = JsArray(profile.email)
        ),
        Message = Message(
          Body = Body(
            Text = Content(
              Data = s"Hi ${profile.firstName} ${profile.lastName}, your profile was uploaded successfully in our system"
            )
          ),
          Subject = Content(
            Data = "Profile Uploaded Successfully"
          )
        ),
        Source = config.sourceAddress
      )

      Async[G].fromFuture {
        Async[G].pure(client.sendEmailFuture(request))
      }
    }

    for {
      client <- buildSESClient[F](config)
      _ <- sendSESEmail[F](client, profile)
      _ <- Console[F].println(s"${profile.id} email sent successfully.")
    } yield ()
  }

  private def buildDynamoDBClient[F[_] : Applicative](config: ProfileProcessorConfig): F[DynamoDB] = {
    val awsCredentials = new AWSCredentials(config.credentials.accessKeyId, config.credentials.secretAccessKey)
    val awsConfig = AWSConfig(endpoint = config.endpoint, region = config.region, credentials = awsCredentials)

    Applicative[F].pure(new DynamoDB(awsConfig))
  }

  private def buildS3Client[F[_] : Applicative](config: ProfileProcessorConfig): F[S3] = {
    val awsCredentials = new AWSCredentials(config.credentials.accessKeyId, config.credentials.secretAccessKey)
    val awsConfig = AWSConfig(endpoint = config.endpoint, region = config.region, credentials = awsCredentials, s3ForcePathStyle = true)

    Applicative[F].pure(new S3(awsConfig))
  }

  private def buildSESClient[F[_] : Applicative](config: ProfileProcessorConfig): F[SES] = {
    val awsCredentials = new AWSCredentials(config.credentials.accessKeyId, config.credentials.secretAccessKey)
    val awsConfig = AWSConfig(endpoint = config.endpoint, region = config.region, credentials = awsCredentials)

    Applicative[F].pure(new SES(awsConfig))
  }
}
