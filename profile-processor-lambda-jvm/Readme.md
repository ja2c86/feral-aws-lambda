# Profile Processor Lambda for JVM Runtime

## Build

For building this lambda's JAR please execute:

```
$ sbt clean assembly
```

This will generate the lambda's JAR in the `terraform/profile-processor-lambda` folder.

## Deploying Using Terraform

The `terraform` package contains the assets for creating the required infrastructure and deploying this lambda on `localstack` using `terraform`.

The first step is starting localstack docker container:

```
$ cd docker
$ docker compose up
```

Before executing the application the infrastructure should be created using `terraform`:

```
$ cd terraform
$ terraform init
$ terraform apply
```

## Execute the Lambda

To execute the lambda a new profiles file should be placed in the `new-profiles-bucket` S3 bucket.

The results of the execution can be accessed through the localstack web UI (requires Localstack credentials): https://app.localstack.cloud/.

Then the results can be verified in different localstack sections:

- `Cloudwatch lambda's logs`: The output of the process execution.
- `S3 archived-profiles-bucket`: The generated files of the processed profiles.
- `DynamoDB profiles table`: The records of the processed profiles.
- `SES Emails from profile-processor@mail.com`: The notifications of the processed profiles.

## Cleaning Up

After executing the application the created infrastructure can be destroyed using terraform:

```
$ cd terraform
$ terraform destroy
```

Finally, the docker container and its volumes should be removed:

```
$ cd docker
$ docker-compose down --volumes
```

## Useful Documentation
- [Feral Framework](https://github.com/typelevel/feral)
