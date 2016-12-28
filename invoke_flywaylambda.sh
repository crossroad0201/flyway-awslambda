#!/bin/sh

invoke_lambda() {
  aws lambda invoke \
  --invocation-type RequestResponse \
  --function-name 'flyway-awslambda-dbmigration' \
  --region ${REGION} \
  --payload "{\"bucket_name\":\"${BUCKET}\", \"prefix\":\"${PREFIX}\", \"flyway_conf\":\"${CONF}\"}" \
  migration_result.json

  cat migration_result.json
  echo ""
}

show_usage() {
  echo "Invoke Flyway AWS Lambda function."
  echo "Usage..."
  echo "./invoke_flywaylambda.sh -r REGION -b BUCKET_NAME -p PREFIX [-c FLYWAY_CONF] [-h]"
  echo "  -r : AWS Region for Flyway Lambda function."
  echo "  -b : Bucket name Flyway resources deployed."
  echo "  -p : Prefix name Flyway resources deployed."
  echo "  -c : Flyway configuration file name.(Optional. Default 'flyway.conf')"
  echo "  -h : Show help."
  exit 1
}


while getopts r:b:p:c:h OPT
do
  case $OPT in
    r) REGION=$OPTARG
      ;;
    b) BUCKET=$OPTARG
      ;;
    p) PREFIX=$OPTARG
      ;;
    c) CONF=$OPTARG
      ;;
    h) show_usage
      ;;
    \?) show_usage
      ;;
  esac
done
[ "${REGION}" == "" ] && show_usage
[ "${BUCKET}" == "" ] && show_usage
[ "${PREFIX}" == "" ] && show_usage
[ "${CONF}" == "" ] && CONF="flyway.conf"

invoke_lambda
