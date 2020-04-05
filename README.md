# CFP Notification Job

![Build status](https://github.com/this-week-in/cfp-notification-job/workflows/CI/badge.svg)

## Description

This application spins up, finds all the Pinboard bookmarks that have the tag `cfp`, and then finds from among those the bookmarks that _don't_ have a tag for the current year, e.g, `2018`. It then uses SendGrid to send an email that indicates which CFP's (that I have shown an interest in in years prior) I have not submitted for in this year. 

## Deployment

This runs on a scheduled job on the Pivotal Cloud Foundry. You can use CRON expressions to dictate how often a given job should spin up. When it spins up, it'll scale up and run, then scale to zero. See this demo for a simpler example of a [scheduled job on Pivotal Cloud Foundry](https://github.com/joshlong/cf-task-demo).

