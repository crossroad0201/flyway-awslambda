package crossroad0201.aws.flywaylambda.deploy

import java.nio.file.Path
import java.util.{Properties => JProperties}

case class FlywayDeployment(
  sourceBucket: String,
  sourcePrefix:String,
  url: String,
  user: String,
  password: String,
  location: String,
  sqlFiles: Seq[Path]
)
object FlywayDeployment {
  def apply(sourceBucket: String, sourcePrefix:String, conf: JProperties, location: String, sqlFiles: Seq[Path]): FlywayDeployment = {
    FlywayDeployment(
      sourceBucket,
      sourcePrefix,
      conf.getProperty("flyway.url"),
      conf.getProperty("flyway.user"),
      conf.getProperty("flyway.password"),
      location,
      sqlFiles
    )
  }
}
