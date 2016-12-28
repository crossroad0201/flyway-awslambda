package crossroad0201.aws.flywaylambda

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.runtime.events.S3Event
import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}

import scala.util.{Failure, Success}

class S3EventMigrationHandler extends RequestHandler[S3Event, Unit] with S3MigrationHandlerBase {

  override def handleRequest(event: S3Event, context: Context): Unit = {
    val logger = context.getLogger

    implicit val s3Client: AmazonS3 = new AmazonS3Client().withRegion(Region.getRegion(Regions.fromName(event.getRecords.get(0).getAwsRegion)))

    logger.log(s"Flyway migration start. by ${event.getRecords.get(0).getEventName} s3://${event.getRecords.get(0).getS3.getBucket.getName}/${event.getRecords.get(0).getS3.getObject.getKey}")

    val s3 = event.getRecords.get(0).getS3
    val migrationPrefix = {
      val objectKey = s3.getObject.getKey
      objectKey.substring(0, objectKey.lastIndexOf("/"))
    }

    migrate(s3.getBucket.getName, migrationPrefix)(context, s3Client) match {
      case Success(r) => logger.log(r)
      case Failure(e) => e.printStackTrace()
    }
  }

}

