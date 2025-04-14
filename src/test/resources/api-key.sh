#!/bin/bash
set -euo pipefail

echo "Creating API key..."

curl -X POST -kL --retry 5 --retry-max-time 120 \
    -u "${ELASTICSEARCH_USERNAME}:${ELASTICSEARCH_PASSWORD}" \
    "${ELASTICSEARCH_HOSTS}/_security/api_key" \
    -d "{\"name\": \"edot-api-key-${RANDOM}\"}" \
    -H "Content-Type: application/json" > /edot/api-key.json
echo "Extracting API key..."
grep -Eo '"encoded\":\"[A-Za-z0-9+/=]+' /edot/api-key.json | grep -Eo '[A-Za-z0-9+/=]+' | tail -n 1 > /edot/api-key.txt
echo "Creating confiuration file..."
sed "s/\${env:ELASTIC_API_KEY}/$(cat /edot/api-key.txt)/g" /edot.yml > /edot/edot.yml
cat /edot/edot.yml
