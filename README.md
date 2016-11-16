Flyway AWS Lambda function.
===========================
 
# What's this?

Lambda function for AWS RDS Migration using [Flyway](https://flywaydb.org).

EC2 instance is not necessary for DB migration.


# Setup

## S3 bucket

Create S3 bucket and folder for Flyway resources.
 
### Bucket structure

```
s3://my-flyway             <- Flyway migration bucket.
  - /my-application        <- Flyway resource folder(prefix).
    - flyway.conf          <- Flyway configuration file.
    - V1__create_foo.sql   <- SQL file(s)
    - V2__create_bar.sql
```

### Flyway configuration

create Flyway configuration file named `flyway.conf` in resource folder.

```
flyway.url = jdbc:mysql://RDS_ENDPOINT/DATABSE_NAME
flyway.user = USER_NAME
flyway.password = PASSWORD
```

## AWS Settings

### VPC Endpoint 

Require [VPC Endpoint](http://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/vpc-endpoints.html) for access to S3 Bucket from Lambda function in VPC.


## Deploy Lambda function

### Code

* Download jar module from releases
  * Or build from source.(Require JDK, Scala, sbt)
```
sbt assembly
```

* Upload `flyway-awslambda-x.x.x.jar`.

### Configuration

||value|
|----|----|
|Runtime|`Java 8`|
|Handler|`crossroad0201.aws.flywaylambda.S3EventMigrationHandler::handleRequest`|
|Role|See `Role` section.|
|Timeout|`5 min.`|
|VPC|Same VPC as target RDS.|

#### Role

Require policies.

* AmazonRDSFullAccess
* AmazonS3FullAccess
* AmazonLambdaVPCAccessExecutionRole

### Triggers

Add trigger `S3 to Lambda`.

||value|Example|
|----|----|----|
|Bucket|Your Flyway migration bucket.|`my-flyway`|
|Event type|`Object created`|-|
|Prefix|Your Flyway migration files location.|`my-application/`|
|Suffix|`sql`|-|


# Run

Put Flyway SQL file into S3 resource folder.

Invoke flyway-lambda automatically by S3 event.

Check `migration-result.json` in S3 resource folder for result,
and CloudWatch log for more detail.
