#!/usr/bin/env bash

APP_NAME=cfp-notification-job
JOB_NAME=$APP_NAME


SCHEDULER_NAME=scheduler-joshlong

cf push -b java_buildpack -u none --no-route -p target/${APP_NAME}.jar ${APP_NAME}

# scheduler
cf s | grep ${SCHEDULER_NAME} || cf cs scheduler-for-pcf standard ${SCHEDULER_NAME}
cf bs ${APP_NAME} ${SCHEDULER_NAME}


cf set-env $APP_NAME PINBOARD_TOKEN $PINBOARD_TOKEN

cf set-env $APP_NAME AWS_ACCESS_KEY_ID $AWS_ACCESS_KEY_ID
cf set-env $APP_NAME AWS_ACCOUNT_ID $AWS_ACCOUNT_ID
cf set-env $APP_NAME AWS_SECRET_ACCESS_KEY $AWS_SECRET_ACCESS_KEY
cf set-env $APP_NAME AWS_REGION $AWS_REGION

cf set-env $APP_NAME SENDGRID_API_KEY $SENDGRID_API_KEY

cf set-env $APP_NAME CFP_NOTIFICATIONS_SOURCE_NAME $CFP_NOTIFICATIONS_SOURCE_NAME
cf set-env $APP_NAME CFP_NOTIFICATIONS_SOURCE_EMAIL $CFP_NOTIFICATIONS_SOURCE_EMAIL

cf set-env $APP_NAME CFP_NOTIFICATIONS_DESTINATION_EMAIL $CFP_NOTIFICATIONS_DESTINATION_EMAIL
cf set-env $APP_NAME CFP_NOTIFICATIONS_DESTINATION_NAME $CFP_NOTIFICATIONS_DESTINATION_NAME

cf restage $APP_NAME


# job management requires a specialized plugin for the `cf` CLI. See `cf.sh`.
cf jobs | grep $JOB_NAME && cf delete-job -f $JOB_NAME
cf create-job $APP_NAME $JOB_NAME ".java-buildpack/open_jdk_jre/bin/java org.springframework.boot.loader.JarLauncher"
cf run-job $JOB_NAME
cf schedule-job $JOB_NAME "*/2 * * * ? *"
#cf schedule-job $JOB_NAME "0 20 ? * *"
#cf schedule-job cfp-notifications "0 1 ? * *"
