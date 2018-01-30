Flyway AWS Lambda function.
===========================
 
# What's this?

Lambda function for AWS RDS Migration using [Flyway](https://flywaydb.org).

EC2 instance is not necessary for DB migration.

This Lambda function is supporting 2 migration methods.

1. Automatic migration by put SQL file into S3.
1. Manual migration by invoking yourself. (Since 0.2.0)

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

#### Supported options.

See [Flyway - Config Files](https://flywaydb.org/documentation/configfiles) for option details.

* `flyway.outOfOrder`

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
|Handler|See `Handler` section.|
|Role|See `Role` section.|
|Timeout|`5 min.`|
|VPC|Same VPC as target RDS.|

#### Handler

* `crossroad0201.aws.flywaylambda.S3EventMigrationHandler`  
Run migration automatically when put SQL file into S3 bucket.

* `crossroad0201.aws.flywaylambda.InvokeMigrationHandler` (Since 0.2.0)  
Run migration invoke Lambda function yourself.

#### Role

Require policies.

* AmazonRDSFullAccess
* AmazonS3FullAccess
* AmazonLambdaVPCAccessExecutionRole

### Triggers

Require setting trigger `S3 to Lambda` if using `S3EventMigrationHandler`.

||value|Example|
|----|----|----|
|Bucket|Your Flyway migration bucket.|`my-flyway`|
|Event type|`Object created`|-|
|Prefix|Your Flyway migration files location.|`my-application/`|
|Suffix|`sql`|-|


# Setup by CloudFormation

You can setup `flyway-awslambda` automatically using CloudFormation.  
See sample templates in `src/main/aws`. 

* `flyway-awslambda-x.x.x.jar` module put in your any bucket.

* Create stack by template `1-rds.yaml`.
Create RDS Aurora cluster.

* Create stack by template `2-flyway-awslambda.yaml`.
Create **flyway-awslambda** function.

* Put `flyway.conf` configuration file in Flyway migration bucket.

## Note

* Require delete ENI(Elastic Network Interface) entry for VPC Lambda before delete stack `1-rds.yaml`.  
A ENI entry for VPC Lambda create by stack `2-flyway-awslambda.yaml`, but this ENI entry does not delete automatically.

# Run

## Using S3EventMigrationHandler

Put Flyway SQL file into S3 resource folder.(**one by one!!!**)

Invoke flyway-lambda automatically by S3 event.

Check `migration-result.json` in S3 resource folder for result,
and CloudWatch log for more detail.

## Using InvokeMigrationHandler

Put Flyway SQL file(s) into S3 resource folder.
 
And invoke flyway-lambda function yourself with the following json payload.
(invoke by AWS console, CLI, any application...etc. see [CLI example](./invoke_flywaylambda.sh))

```json
{
  "bucket_name": "my-flyway",
  "prefix": "my-application",
  "flyway_conf": "flyway.conf"  // Optional. Default 'flyway.conf'
}
```

Check result message or `migration-result.json` in S3 resource folder for result,
and CloudWatch log for more detail.
