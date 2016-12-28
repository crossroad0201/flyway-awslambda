package crossroad0201.aws.flywaylambda.migration

import java.util.Date

import org.flywaydb.core.api.{MigrationInfo => FlywayMigrationInfo}

case class MigrationResult(
  last_status: String,
  rdsUrl: String,
  appliedCount: Int,
  message: String,
  infos: Seq[MigrationInfo])
object MigrationResult {
  def success(rdsUrl: String, appliedCount: Int, infos: Seq[MigrationInfo]): MigrationResult = {
    MigrationResult("SUCCESS", rdsUrl, appliedCount, "Migration success", infos)
  }
  def failure(rdsUrl: String, cause: Throwable, infos: Seq[MigrationInfo]): MigrationResult = {
    MigrationResult("FAILURE", rdsUrl, 0, s"Migration failed by ${cause.toString}", infos)
  }
}

case class MigrationInfo(
  version: String,
  `type`: String,
  installedAt: Date,
  state: String,
  execTime: Int,
  description: String)
object MigrationInfo {
  def apply(i : FlywayMigrationInfo): MigrationInfo = {
    MigrationInfo(i.getVersion.getVersion, i.getType.name, i.getInstalledOn, i.getState.name, i.getExecutionTime, i.getDescription)
  }
}


