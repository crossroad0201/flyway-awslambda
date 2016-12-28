
lazy val flywayAwsLambda = (project in file(".")).settings(
  organization := "crossroad0201.aws",
  name := "flyway-awslambda",
  version := "0.2.0-SNAPSHOT",
  scalaVersion := "2.12.0",

  assemblyJarName := s"${name.value}-${version.value}.jar",
  test in assembly := {},

  libraryDependencies ++= Seq(
    // Flyway
    "org.flywaydb" % "flyway-core" % "4.0.3",
    "mysql" % "mysql-connector-java" % "6.0.5", // Flyway supports only Ver.6 higher.

    // AWS
    "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
    "com.amazonaws" % "aws-lambda-java-events" % "1.3.0",
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.53",

    // Json
    "io.spray" %%  "spray-json" % "1.3.2",

    // Test
    "org.scalatest" %% "scalatest" % "3.0.0" % Test
  )
)
