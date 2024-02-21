
scalaVersion := "2.13.12"

name := "profile-processor-lambda"
organization := "co.fullstacklabs"
version := "1.0"

enablePlugins(ScalaJSPlugin)
enablePlugins(ScalaJSBundlerPlugin)
enablePlugins(LambdaJSPlugin) // feral-lambda

scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))

val awsSdkScalajsFacadeVersion = "0.33.0-v2.892.0"
val awsSdkVersion = "2.892.0"
val uuidVersion = "^3.4.0"

libraryDependencies ++= Seq(
  "net.exoego" %%% "aws-sdk-scalajs-facade" % awsSdkScalajsFacadeVersion
)

// the dependencies used by ScalaJSBundlerPlugin
Compile / npmDependencies ++= Seq(
  "aws-sdk" -> awsSdkVersion,
  "uuid"    -> uuidVersion
)

Compile / npmPackageOutputDirectory := file(s"${baseDirectory.value}/terraform/profile-processor-lambda")

// the dependencies that will be included in the generated package.json
npmPackageDependencies ++= Seq(
  "uuid"    -> uuidVersion
)
