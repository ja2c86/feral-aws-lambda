package object model {

  case class ProfileProcessorConfig(endpoint: String, region: String, credentials: Credentials, tableName: String, newBucketName: String, archivedBucketName: String, sourceAddress: String, totalWorkers: Int)
  case class Credentials(accessKeyId: String, secretAccessKey: String)

  case class Profile(id: String, firstName: String, lastName: String, email: String, address: String, phoneNumber: String, age: Int) {
    override def toString: String =
      s"""First Name: $firstName
        |Last Name: $lastName
        |Email: $email
        |Address: $address
        |Phone Number: $phoneNumber
        |Age: $age""".stripMargin
  }

}
