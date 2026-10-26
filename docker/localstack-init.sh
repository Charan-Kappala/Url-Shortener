#!/bin/sh
# Runs inside LocalStack once it is ready: creates the click-events queue and its dead-letter queue.
# A message that fails processing 5 times (e.g. it cannot be parsed) moves to the DLQ instead of looping forever.
set -e
awslocal sqs create-queue --queue-name click-events-dlq
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url http://localhost:4566/000000000000/click-events-dlq \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)
awslocal sqs create-queue --queue-name click-events \
  --attributes "{\"VisibilityTimeout\":\"30\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}"
