package crossroad0201.aws.flywaylambda.deploy

import java.nio.file.Path
import java.util.{ Properties => JProperties }

import org.flywaydb.core.Flyway

case class FlywayDeployment(
  sourceBucket: String,
  sourcePrefix:String,
  url: String,
  user: String,
  password: String,
  location: String,
  sqlFiles: Seq[Path],
  options: Seq[FlywayOption]
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
      sqlFiles,
      FlywayOption.buildOptions(conf)
    )
  }
}

sealed trait FlywayOption {
  def apply(flyway: Flyway): Flyway
}
object FlywayOption {
  def buildOptions(conf: JProperties): Seq[FlywayOption] = {
    import scala.collection.JavaConverters._
    conf.asScala.flatMap {
      case ("flyway.outOfOrder", enabled) => Some(OutOfOrder(enabled.toBoolean))
      case _ => None
    }.toSeq
  }
}
case class OutOfOrder(enabled: Boolean) extends FlywayOption {
  override def apply(flyway: Flyway) = {
    flyway.setOutOfOrder(enabled)
    flyway
  }
}
