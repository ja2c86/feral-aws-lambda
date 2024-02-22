package com.example.model

import io.circe.Decoder

final case class S3Event(
                           records: List[S3EventRecord]
                         )

object S3Event {
  implicit val decoder: Decoder[S3Event] =
    Decoder.forProduct1("Records")(S3Event.apply)
}

final case class S3EventRecord(
                                eventVersion: String,
                                eventName: String,
                                s3: S3Message
                               )

object S3EventRecord {
  implicit val decoder: Decoder[S3EventRecord] = Decoder.forProduct3(
    "eventVersion",
    "eventName",
    "s3"
  )(S3EventRecord.apply)
}

final case class S3Message(
                            bucket: S3Bucket,
                            `object`: S3Object
                           )

object S3Message {
  implicit val decoder: Decoder[S3Message] = Decoder.forProduct2(
    "bucket",
    "object"
  )(S3Message.apply)
}

final case class S3Bucket(
                            name: String,
                            arn: String
                          )

object S3Bucket {
  implicit val decoder: Decoder[S3Bucket] = Decoder.forProduct2(
    "name",
    "arn"
  )(S3Bucket.apply)
}

final case class S3Object(
                           key: String,
                           size: Long
                         )

object S3Object {
  implicit val decoder: Decoder[S3Object] = Decoder.forProduct2(
    "key",
    "size"
  )(S3Object.apply)
}
