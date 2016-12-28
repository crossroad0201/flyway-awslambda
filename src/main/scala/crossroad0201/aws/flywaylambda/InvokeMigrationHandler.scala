package crossroad0201.aws.flywaylambda

import java.io.{BufferedOutputStream, InputStream, OutputStream, PrintWriter}

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}

import scala.io.{BufferedSource, Codec}
import scala.util.{Failure, Success, Try}

class InvokeMigrationHandler extends RequestStreamHandler with S3MigrationHandlerBase {
  type BucketName = String
  type Prefix = String
  type ConfFileName = String

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    def parseInput: Try[(BucketName, Prefix, ConfFileName)] = Try {
      import spray.json._
      import DefaultJsonProtocol._

      val json = new BufferedSource(input)(Codec("UTF-8")).mkString
      val jsObj = JsonParser(json).toJson.asJsObject
      jsObj.getFields(
        "bucket_name",
        "prefix"
      ) match {
        case Seq(JsString(b), JsString(p)) => {
          jsObj.getFields(
            "flyway_conf"
          ) match {
            case Seq(JsString(c)) => (b, p, c)
            case _ => (b, p, "flyway.conf")
          }
        }
        case _ => throw new IllegalArgumentException(s"Missing require key [bucketName, prefix]. - $json")
      }
    }

    val logger = context.getLogger

    implicit val s3Client: AmazonS3 = new AmazonS3Client().withRegion(Region.getRegion(Regions.fromName(sys.env("AWS_REGION"))))

    (for {
      i <- parseInput
      _ = { logger.log(s"Flyway migration start. by invoke lambda function(${i._1}, ${i._2}, ${i._3}).") }
      r <- migrate(i._1, i._2, i._3)(context, s3Client)
    } yield r) match {
      case Success(r) =>
        logger.log(r)
        val b = r.getBytes("UTF-8")
        val bout = new BufferedOutputStream(output)
        Stream.continually(bout.write(b))
        bout.flush()
      case Failure(e) =>
        e.printStackTrace()
        val w = new PrintWriter(output)
        w.write(e.toString)
        w.flush()
    }
  }

}
