# Profiles Generator Script

This [scala-cli](https://scala-cli.virtuslab.org/) script uses [java-faker](https://github.com/DiUS/java-faker) for generating fake profiles to be processed by the lambda.

Edit the `ProfilesGenerator.scala` specifying the number of profiles to generate and the desired file name. Then execute the script with the following command:

```
$ scala-cli run ProfilesGenerator.scala
```
