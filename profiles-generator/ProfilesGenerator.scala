//> using scala "3.3.1"
//> using dep "co.fs2::fs2-core::3.9.4"
//> using dep "co.fs2::fs2-io::3.9.4"
//> using dep "com.github.javafaker:javafaker:1.0.2"
//> using dep "org.typelevel::cats-effect::3.5.3"

import cats.effect._
import com.github.javafaker.Faker
import fs2._
import fs2.io.file.{Files, Path}

import java.util.Locale

case class Profile(firstName: String, lastName: String, email: String, address: String, phoneNumber: String, age: Int)

object ProfilesGenerator extends IOApp.Simple:
  val totalProfiles = 1000
  val filePath = "generated-profiles.csv"

  def generateProfile: IO[Profile] =
    val faker = new Faker(Locale.US)
    val profile = Profile(
        firstName = faker.name().firstName(),
        lastName = faker.name().lastName(),
        email = faker.internet().emailAddress(),
        address = faker.address().fullAddress(),
        phoneNumber = faker.phoneNumber().phoneNumber(),
        age = faker.number().numberBetween(18, 100)
      )
    IO(profile)

  def formatProfile(profile: Profile): IO[String] =
    IO(s"${profile.firstName}|${profile.lastName}|${profile.email}|${profile.address}|${profile.phoneNumber}|${profile.age}\n")

  def generateFile(x: Int): IO[Unit] =
    Stream.range(0, x)
      .evalMap(_ => generateProfile)
      .evalMap(formatProfile)
      .through(text.utf8.encode)
      .through(Files[IO].writeAll(Path(filePath)))
      .compile
      .drain

  def run = generateFile(totalProfiles) >> IO.println(s"File $filePath generated.")
