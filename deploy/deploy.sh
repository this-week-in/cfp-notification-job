#!/usr/bin/env bash

cf push -b java_buildpack --health-check-type none  --no-route  -p target/cfp-job.jar cfp-job
cf s | grep scheduler-joshlong || cf cs scheduler-for-pcf standard scheduler-joshlong
cf bs cfp-job scheduler-joshlong


cf set-env cfp-job PINBOARD_TOKEN $PINBOARD_TOKEN
cf set-env cfp-job SENDGRID_API_KEY $SENDGRID_API_KEY

cf set-env cfp-job CFP_NOTIFICATIONS_SOURCE_NAME $CFP_NOTIFICATIONS_SOURCE_NAME
cf set-env cfp-job CFP_NOTIFICATIONS_SOURCE_EMAIL $CFP_NOTIFICATIONS_SOURCE_EMAIL

cf set-env cfp-job CFP_NOTIFICATIONS_DESTINATION_EMAIL $CFP_NOTIFICATIONS_DESTINATION_EMAIL
cf set-env cfp-job CFP_NOTIFICATIONS_DESTINATION_NAME $CFP_NOTIFICATIONS_DESTINATION_NAME

cf restage cfp-job


# job management requires a specialized plugin for the `cf` CLI. See `cf.sh`.

cf delete-job -f cfp-notifications
cf create-job cfp-job cfp-notifications ".java-buildpack/open_jdk_jre/bin/java org.springframework.boot.loader.JarLauncher"
cf run-job cfp-notifications
cf schedule-job cfp-notifications "0 20 ? * *"
#cf schedule-job cfp-notifications "0 1 ? * *"
