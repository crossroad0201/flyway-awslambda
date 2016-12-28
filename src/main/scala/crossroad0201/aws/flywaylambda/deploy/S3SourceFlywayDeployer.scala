package crossroad0201.aws.flywaylambda.deploy

import java.nio.file.{Files, Path, Paths}
import java.util.{Properties => JProperties}

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.S3ObjectSummary

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.Try

class S3SourceFlywayDeployer(s3Client: AmazonS3, srcBucketName: String, srcPrefix: String) extends FlywayDeployer {

  def deploy(flywayConfFileName: String)(implicit context: Context): Try[FlywayDeployment] = Try {
    val logger = context.getLogger

    val tmpDir = Files.createDirectories(Paths.get("/tmp", context.getAwsRequestId))

    @tailrec
    def deployInternal(objects: List[S3ObjectSummary], acc: (Option[JProperties], ListBuffer[Path])): (Option[JProperties], Seq[Path]) = {
      def loadConf(key: String) = {
        val o = s3Client.getObject(srcBucketName, key)
        val props = new JProperties
        props.load(o.getObjectContent)
        logger.log(s"Flyway configuration loaded. s3://$srcBucketName/$key")
        (Some(props), acc._2)
      }
      def createDir(key: String) = {
        val dir = Files.createDirectories(Paths.get(tmpDir.toString, key))
        logger.log(s"Dir created. $dir")
        acc
      }
      def createSqlFile(key: String) = {
        val o = s3Client.getObject(srcBucketName, key)
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
            case key if key.endsWith(flywayConfFileName) => loadConf(key)
            case key if key.endsWith("/") => createDir(key)
            case key if key.endsWith(".sql") => createSqlFile(key)
            case _ => acc
          }
          deployInternal(xs, _acc)
      }
    }

    val objectSummaries = {
      val objects = s3Client.listObjects(srcBucketName, srcPrefix)
      objects.getObjectSummaries.asScala.toList.sortWith { (x, y) =>
        x.getKey.compareTo(y.getKey) < 1
      }
    }
    deployInternal(objectSummaries, (None, ListBuffer())) match {
      case (Some(conf), sqlFiles) =>
        FlywayDeployment(
          srcBucketName,
          srcPrefix,
          conf,
          s"filesystem:${Paths.get(tmpDir.toString, srcPrefix).toString}",
          sqlFiles)
      case _ => throw new IllegalStateException(s"$flywayConfFileName does not exists.")
    }
  }

}
