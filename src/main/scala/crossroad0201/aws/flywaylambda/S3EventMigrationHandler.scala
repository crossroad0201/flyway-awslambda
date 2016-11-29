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
import scala.util.{Failure, Success, Try}

class S3EventMigrationHandler extends RequestHandler[S3Event, String] {
  val FlywayConfFileName = "flyway.conf"

  override def handleRequest(event: S3Event, context: Context): String = {
    val logger = context.getLogger

    val record = event.getRecords.get(0)
    implicit val s3Client: AmazonS3Client = new AmazonS3Client().withRegion(Region.getRegion(Regions.fromName(record.getAwsRegion)))

    logger.log(s"Flyway migration start. by ${record.getEventName} s3://${record.getS3.getBucket.getName}/${record.getS3.getObject.getKey}")

    val result = for {
      r <- migrate(record)(context, s3Client)
      d = r._1
      m = r._2
      _ = {
        logger.log(s"${m.message}!. ${m.appliedCount} applied.")
        m.infos.foreach { i =>
          logger.log(s"Version=${i.version}, Type=${i.`type`}, State=${i.state} InstalledAt=${i.installedAt} ExecutionTime=${i.execTime} Description=${i.description}")
        }
      }
      p <- storeResult(d, m)
      _ = logger.log(s"Migration result stored to $p.")
    } yield m

    result match {
      case Success(r) => r.message
      case Failure(e) =>
        e.printStackTrace()
        e.toString
    }
  }

  private def migrate(record: S3EventNotificationRecord)(implicit context: Context, s3Client: AmazonS3Client) = {
    val logger = context.getLogger

    val s3 = record.getS3
    val bucket = s3.getBucket

    def deployFlywayResources(s3: S3Entity) = Try {
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
              case _ => acc
            }
            deployInternal(xs, _acc)
        }
      }

      val migrationPrefix = {
        val objectKey = s3.getObject.getKey
        objectKey.substring(0, objectKey.lastIndexOf("/"))
      }
      val objectSummaries = {
        val objects = s3Client.listObjects(bucket.getName, migrationPrefix)
        objects.getObjectSummaries.asScala.toList.sortWith { (x, y) =>
          x.getKey.compareTo(y.getKey) < 1
        }
      }
      deployInternal(objectSummaries, (None, ListBuffer())) match {
        case (Some(conf), sqlFiles) =>
          FlywayDeployment(
            bucket.getName,
            migrationPrefix,
            conf,
            s"filesystem:${Paths.get(tmpDir.toString, migrationPrefix).toString}",
            sqlFiles)
        case _ => throw new IllegalStateException(s"$FlywayConfFileName does not exists.")
      }
    }

    def flywayMigrate(deployment: FlywayDeployment) = {
      val flyway = new Flyway

      val appliedCount = Try {
        flyway.setDataSource(
          deployment.url,
          deployment.user,
          deployment.password
        )
        flyway.setLocations(deployment.location)

        flyway.migrate
      }

      val migrationInfos = Try {
        flyway.info.all
      }

      (appliedCount, migrationInfos) match {
        case (Success(c), Success(is)) => MigrationResult.success(deployment.url, c, is.map(MigrationInfo(_)))
        case (Success(c), Failure(e)) => MigrationResult.failure(deployment.url, e, Seq())
        case (Failure(e), Success(is)) => MigrationResult.failure(deployment.url, e, is.map(MigrationInfo(_)))
        case (Failure(e1), Failure(e2)) => MigrationResult.failure(deployment.url, e1, Seq())
      }
    }

    for {
      d <- deployFlywayResources(s3)
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
      r = flywayMigrate(d)
    } yield (d, r)
  }

  private def storeResult(deployment: FlywayDeployment, result: MigrationResult)(implicit s3Client: AmazonS3Client) = Try {
    import MigrationResultProtocol._
    import spray.json._

    val json = result.toJson.prettyPrint

    val jsonPath = s"${deployment.sourcePrefix}/migration-result.json"
    val putResult = s3Client.putObject(deployment.sourceBucket, jsonPath, json)

    s"s3://${deployment.sourceBucket}/${jsonPath}"
  }


}

