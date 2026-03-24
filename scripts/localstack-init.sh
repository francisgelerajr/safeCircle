#!/bin/bash
# Runs inside LocalStack on startup.
# Creates the SQS queue your Spring Boot app needs locally.
#
# awslocal is a thin wrapper around the AWS CLI that points at LocalStack.
# You can also run these manually: docker exec safecircle-localstack awslocal sqs list-queues

echo "Initialising LocalStack resources..."

# Create the notification queue
awslocal sqs create-queue \
  --queue-name safecircle-notifications \
  --region ap-southeast-1

# Create a dead-letter queue for failed jobs
awslocal sqs create-queue \
  --queue-name safecircle-notifications-dlq \
  --region ap-southeast-1

echo "LocalStack init complete."