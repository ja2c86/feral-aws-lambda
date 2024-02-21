# DynamoDB tables are schemaless; other than the primary key, you do not need to
# define any extra attributes or data types when you create a table.
resource "aws_dynamodb_table" "profiles-table" {
  name           = "profiles"
  hash_key       = "id"
  billing_mode   = "PAY_PER_REQUEST"

  attribute {
    name = "id"
    type = "S"
  }
}
