# Profile Processor Lambda

## Build

For building this lambda's JS please execute:

```
$ rm -rf target/ project/target terraform/profile-processor-lambda
$ sbt clean npmPackage
```

This will generate the lambda's JS in the `terraform/profile-processor-lambda` folder.

## Deploying Using Terraform

The `terraform` package contains the assets for creating the required infrastructure and deploying this lambda on `localstack` using `terraform`.

For building the lambda step `npm` and `zip` should be available.

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

## Note - AWS Layers

This application's lambdas are created using Scala-JS and Feral, they have a npm dependency (`uuid`), currently the `package.json` of each lambda is included and `terraform` installs each lambda dependencies before packaging.

This issue can also be solved using AWS Layers (which are not supported in the `Localstack` community edition), to use layers for dependency handling instead packaging the lambdas with all its dependencies the following changes should be done in the application

## Useful Documentation
- [Feral Framework](https://github.com/typelevel/feral)
- [aws-sdk-scalajs-facade](https://github.com/exoego/aws-sdk-scalajs-facade)
- [ScalaJS Types](https://www.scala-js.org/doc/interoperability/types.html)
- [ScalaJS Facade Types](https://www.scala-js.org/doc/interoperability/facade-types.html)
