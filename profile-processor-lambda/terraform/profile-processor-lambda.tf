resource "terraform_data" "profile-processor-lambda-dependencies" {
  provisioner "local-exec" {
    command     = "npm install && zip -r profile-processor-lambda.zip ."
    working_dir = "${path.module}/profile-processor-lambda"
  }
}

resource "aws_s3_object" "profile-processor-lambda-object" {
  bucket = aws_s3_bucket.deployment-bucket.id
  key    = "profile-processor-lambda.zip"
  source = "${path.module}/profile-processor-lambda/profile-processor-lambda.zip"

  depends_on = [terraform_data.profile-processor-lambda-dependencies]
}

resource "aws_lambda_function" "profile-processor-lambda" {
  function_name = "profile-processor"

  s3_bucket = aws_s3_bucket.deployment-bucket.id
  s3_key    = aws_s3_object.profile-processor-lambda-object.key

  runtime   = "nodejs14.x"
  handler   = "index.ProfileProcessor"
  timeout   = 30

  role = aws_iam_role.lambda-exec-role.arn

  environment {
    variables = {
      REGION = "${var.aws_region}"
      ACCESS_KEY = "${var.aws_access_key}"
      SECRET_KEY = "${var.aws_secret_access_key}"
      DYNAMODB_TABLE = "${aws_dynamodb_table.profiles-table.name}"
      NEW_BUCKET_NAME = "${aws_s3_bucket.new-profiles-bucket.bucket}"
      ARCHIVED_BUCKET_NAME = "${aws_s3_bucket.archived-profiles-bucket.bucket}"
      SES_SOURCE_ADDRESS = "${aws_ses_email_identity.my-email.email}"
      TOTAL_WORKERS = "${var.number_workers}"
    }
  }
}

resource "aws_cloudwatch_log_group" "profile-processor-lambda-log-group" {
  name              = "/aws/lambda/${aws_lambda_function.profile-processor-lambda.function_name}"
  retention_in_days = 7
}
