package crossroad0201.aws.flywaylambda

import java.nio.file.{Files, Path, Paths}
import java.util.{Properties => JProperties}

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.event.S3EventNotification.{S3Entity, S3EventNotificationRecord}
import com.amazonaws.services.s3.model.S3ObjectSummary
import org.flywaydb.core.Flyway

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

class S3EventMigrationHandler extends RequestHandler[S3Event, String] {
  val FlywayConfFileName = "flyway.conf"

  override def handleRequest(event: S3Event, context: Context): String = {
    val logger = context.getLogger

    val record = event.getRecords.get(0)
    val successCount = handleS3Event(record)(context)

    val message = s"Flyway migration(s) finished. $successCount"
    logger.log(message)

    // TODO Put migration result to S3 as JSON

    message
  }

  private def handleS3Event(record: S3EventNotificationRecord)(implicit context: Context) = {
    val logger = context.getLogger

    val s3 = record.getS3
    val bucket = s3.getBucket

    logger.log(s"Flyway migration start. by ${record.getEventName} s3://${bucket.getName}/${s3.getObject.getKey}")

    def deploy(s3: S3Entity) = Try {
      val s3Client: AmazonS3Client = new AmazonS3Client().withRegion(Region.getRegion(Regions.fromName(record.getAwsRegion)))
      val tmpDir = Files.createDirectories(Paths.get("/tmp", context.getAwsRequestId))

      @tailrec
      def deployInternal(objects: List[S3ObjectSummary], acc: (Option[JProperties], ListBuffer[Path])): (Option[JProperties], Seq[Path]) = {
        def loadConf(key: String) = {
          val o = s3Client.getObject(bucket.getName, key)
          val props = new JProperties
          props.load(o.getObjectContent)
          logger.log(s"Flyway configuration loaded. s3://${bucket.getName}/$key")
          (Some(props), acc._2)
        }
        def createDir(key: String) = {
          val dir = Files.createDirectories(Paths.get(tmpDir.toString, key))
          logger.log(s"Dir created. $dir")
          acc
        }
        def createSqlFile(key: String) = {
          val o = s3Client.getObject(bucket.getName, key)
          val file = Paths.get(tmpDir.toString, key)
          val fileSize = Files.copy(o.getObjectContent, file)
          logger.log(s"SQL file created. $file($fileSize Byte)")
          acc._2 += file
          acc
        }

        objects match {
          case Nil => (acc._1, acc._2)
          case x :: xs =>
            val _acc = x.getKey match {
              case key if key.endsWith(FlywayConfFileName) => loadConf(key)
              case key if key.endsWith("/") => createDir(key)
              case key if key.endsWith(".sql") => createSqlFile(key)
            }
            deployInternal(xs, _acc)
        }
      }

      val migrationPrefix = {
        val objectKey = s3.getObject.getKey
        objectKey.substring(0, objectKey.lastIndexOf("/"))
      }
      val objects = s3Client.listObjects(bucket.getName, migrationPrefix)

      deployInternal(objects.getObjectSummaries.asScala.toList, (None, ListBuffer())) match {
        case (Some(conf), sqlFiles) =>
          FlywayDeployment(
            conf,
            s"filesystem:${Paths.get(tmpDir.toString, migrationPrefix).toString}",
            sqlFiles)
        case _ => throw new IllegalStateException(s"$FlywayConfFileName does not exists.")
      }
    }

    def migrate(deployment: FlywayDeployment) = Try {
      val flyway = new Flyway
      flyway.setDataSource(
        deployment.url,
        deployment.user,
        deployment.password
      )
      flyway.setLocations(deployment.location)

      val successCount = flyway.migrate

      flyway.info.all.foreach { i =>
        logger.log(s"${i.getVersion} ${i.getDescription} ${i.getType} ${i.getState} ${i.getExecutionTime}")
      }

      successCount
    }

    for {
      d <- deploy(s3)
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
      c <- migrate(d)
      _ = logger.log(s"$c migration applied successfully.")
    } yield c
  }
}

case class FlywayDeployment(url: String, user: String, password: String, location: String, sqlFiles: Seq[Path])
object FlywayDeployment {
  def apply(conf: JProperties, location: String, sqlFiles: Seq[Path]): FlywayDeployment = {
    FlywayDeployment(
      conf.getProperty("flyway.url"),
      conf.getProperty("flyway.user"),
      conf.getProperty("flyway.password"),
      location,
      sqlFiles
    )
  }
}
