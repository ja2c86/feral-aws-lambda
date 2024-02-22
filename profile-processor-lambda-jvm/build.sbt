
scalaVersion := "2.13.12"

name := "profile-processor-lambda-jvm"
organization := "co.fullstacklabs"
version := "1.0"

val feralLambdaVersion = "0.3.0-RC2"
val awsSdkVersion = "2.20.162"

libraryDependencies ++= Seq(
  "org.typelevel"           %%  "feral-lambda"  % feralLambdaVersion,
  "software.amazon.awssdk"  %   "dynamodb"      % awsSdkVersion,
  "software.amazon.awssdk"  %   "iam"           % awsSdkVersion,
  "software.amazon.awssdk"  %   "lambda"        % awsSdkVersion,
  "software.amazon.awssdk"  %   "s3"            % awsSdkVersion,
  "software.amazon.awssdk"  %   "ses"           % awsSdkVersion
)

// to generate jar: $ sbt clean assembly
// assembly / assemblyJarName := "profile-processor-lambda.jar"
assembly / assemblyOutputPath := file(s"${baseDirectory.value}/terraform/profile-processor-lambda.jar")
assembly / assemblyMergeStrategy := {
  case x if x.endsWith("module-info.class") => MergeStrategy.last
  case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.last
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
