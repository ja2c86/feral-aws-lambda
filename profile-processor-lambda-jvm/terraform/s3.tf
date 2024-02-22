resource "aws_s3_bucket" "new-profiles-bucket" {
  bucket        = "new-profiles-bucket"
  force_destroy = true
}

resource "aws_s3_bucket" "archived-profiles-bucket" {
  bucket        = "archived-profiles-bucket"
  force_destroy = true
}

# notifies the lambda when an object is added to the bucket
resource "aws_s3_bucket_notification" "new-profiles-bucket-notification" {
  bucket          = aws_s3_bucket.new-profiles-bucket.id

  lambda_function {
    events              = ["s3:ObjectCreated:*"]
    lambda_function_arn = aws_lambda_function.profile-processor-lambda.arn
  }
}
