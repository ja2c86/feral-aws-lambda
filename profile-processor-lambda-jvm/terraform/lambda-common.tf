resource "aws_iam_role" "lambda-exec-role" {
  name = "lambda-exec-role"
  assume_role_policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Service": "lambda.amazonaws.com"
    },
    "Action": "sts:AssumeRole"
  }]
}
POLICY
}

resource "aws_iam_policy" "lambda-dynamodb-policy" {
  name   = "dynamodb-policy"
  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "dynamodb:*"
    ],
    "Resource": "${aws_dynamodb_table.profiles-table.arn}"
  }]
}
POLICY
}

resource "aws_iam_role_policy_attachment" "lambda-exec-role-dynamodb-policy" {
  role       = aws_iam_role.lambda-exec-role.name
  policy_arn = aws_iam_policy.lambda-dynamodb-policy.arn
}

resource "aws_iam_policy" "lambda-s3-policy" {
  name   = "s3-policy"
  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "s3:*"
    ],
    "Resource": [
      "${aws_s3_bucket.new-profiles-bucket.arn}",
      "${aws_s3_bucket.archived-profiles-bucket.arn}"
    ]
  }]
}
POLICY
}

resource "aws_iam_role_policy_attachment" "lambda-exec-role-s3-policy" {
  role       = aws_iam_role.lambda-exec-role.name
  policy_arn = aws_iam_policy.lambda-s3-policy.arn
}

resource "aws_iam_policy" "lambda-logging-policy" {
  name   = "logging-policy"
  policy = <<POLICY
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ],
    "Resource": "*"
  }]
}
POLICY
}

resource "aws_iam_role_policy_attachment" "lambda-exec-role-logging-policy" {
  role       = aws_iam_role.lambda-exec-role.name
  policy_arn = aws_iam_policy.lambda-logging-policy.arn
}

# Create a bucket to store lambdas objects
resource "aws_s3_bucket" "deployment-bucket" {
  bucket        = "deployment-bucket"
  force_destroy = true
}
