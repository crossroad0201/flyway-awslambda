package crossroad0201.aws.flywaylambda

import java.text.SimpleDateFormat
import java.util.Date

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.AmazonS3
import crossroad0201.aws.flywaylambda.deploy.{FlywayDeployment, S3SourceFlywayDeployer}
import crossroad0201.aws.flywaylambda.migration.{FlywayMigrator, MigrationInfo, MigrationResult}
import spray.json.DefaultJsonProtocol

import scala.util.Try

object MigrationResultProtocol extends DefaultJsonProtocol {
  import spray.json._

  implicit val DateFormat = new RootJsonFormat[Date] {
    override def write(value: Date): JsValue = if (value == null) JsNull else JsString(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(value))
    override def read(json: JsValue): Date = ???
  }
  implicit val migrationInfoFormat = jsonFormat6(MigrationInfo.apply)
  implicit val migrationResultFormat = jsonFormat5(MigrationResult.apply)
}

trait S3MigrationHandlerBase extends FlywayMigrator {

  type ResultJson = String
  type ResultStoredPath = String

  protected def migrate(bucketName: String, prefix: String, flywayConfFileName: String = "flyway.conf")(implicit context: Context, s3Client: AmazonS3): Try[ResultJson] = {
    val logger = context.getLogger

    def resultJson(result: MigrationResult): ResultJson = {
      import MigrationResultProtocol._
      import spray.json._

      result.toJson.prettyPrint
    }

    def storeResult(deployment: FlywayDeployment, result: MigrationResult): ResultStoredPath = {
      val jsonPath = s"${deployment.sourcePrefix}/migration-result.json"
      s3Client.putObject(deployment.sourceBucket, jsonPath, resultJson(result))
      jsonPath
    }

    for {
      // Deploy Flyway resources.
      d <- new S3SourceFlywayDeployer(s3Client, bucketName, prefix, flywayConfFileName).deploy
      _ = {
        logger.log(
          s"""--- Flyway configuration ------------------------------------
             |flyway.url      = ${d.url}
             |flyway.user     = ${d.user}
             |flyway.password = ${d.password}
             |
             |SQL locations   = ${d.location}
             |SQL files       = ${d.sqlFiles.mkString(", ")}
             |-------------------------------------------------------------
              """.stripMargin)
      }

      // Migrate DB.
      r = migrate(d)
      _ = {
        logger.log(s"${r.message}!. ${r.appliedCount} applied.")
        r.infos.foreach { i =>
          logger.log(s"Version=${i.version}, Type=${i.`type`}, State=${i.state} InstalledAt=${i.installedAt} ExecutionTime=${i.execTime} Description=${i.description}")
        }
      }

      // Store migration result.
      storedPath = storeResult(d, r)
      _ = logger.log(s"Migration result stored to $bucketName/$storedPath.")

    } yield resultJson(r)
  }

}

