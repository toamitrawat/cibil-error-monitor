#!/bin/bash
# Produce sample messages to Error-topic using kafka-console-producer
# Requires kafka console tools; if using docker-compose above, you can run a producer inside the kafka container.
# Example payloads: success and failure messages.

cat <<EOF > /tmp/msg1.json
{"Request": {"product": "CD", "status": "Failure"}}
EOF

cat <<EOF > /tmp/msg2.json
{"Request": {"product": "CD", "status": "Success"}}
EOF

echo "Sending a mix of messages..."
# If kafka console producer is available locally:
if command -v kafka-console-producer >/dev/null 2>&1; then
  kafka-console-producer --broker-list localhost:9092 --topic Error-topic < /tmp/msg1.json
  kafka-console-producer --broker-list localhost:9092 --topic Error-topic < /tmp/msg2.json
else
  echo "kafka-console-producer not found. You can use a REST producer or run inside kafka container."
fi
