package crossroad0201.aws.flywaylambda.migration

import crossroad0201.aws.flywaylambda.deploy.FlywayDeployment
import org.flywaydb.core.Flyway

import scala.util.{Failure, Success, Try}

trait FlywayMigrator {

  def migrate(deployment: FlywayDeployment) = {
    val flyway = new Flyway

    val appliedCount = Try {
      flyway.setDataSource(
        deployment.url,
        deployment.user,
        deployment.password
      )
      flyway.setLocations(deployment.location)

      deployment.options.map(_.apply(flyway))

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

}
