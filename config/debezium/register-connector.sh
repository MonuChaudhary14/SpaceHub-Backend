#!/usr/bin/env bash
set -e

DEBEZIUM_CONNECT_HOST="${DEBEZIUM_CONNECT_HOST:-localhost:8083}"
CONNECTOR_CONFIG_FILE="$(dirname "$0")/postgres-outbox-connector.json"

echo "Waiting for Debezium Kafka Connect at http://${DEBEZIUM_CONNECT_HOST}/connectors to be healthy..."
until curl -s -f "http://${DEBEZIUM_CONNECT_HOST}/connectors" > /dev/null; do
  echo "Connect service not ready yet. Retrying in 3 seconds..."
  sleep 3
done

echo "Registering / Updating SpaceHub PostgreSQL Outbox Connector..."
curl -i -X POST \
  -H "Accept:application/json" \
  -H "Content-Type:application/json" \
  "http://${DEBEZIUM_CONNECT_HOST}/connectors/" \
  -d @"${CONNECTOR_CONFIG_FILE}"

echo -e "\nConnector registration completed. Current connector status:"
curl -s "http://${DEBEZIUM_CONNECT_HOST}/connectors/spacehub-postgres-outbox-connector/status"
