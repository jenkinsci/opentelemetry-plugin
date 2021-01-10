#!/usr/bin/env bash

export OPEN_TELEMETRY_COLLECTOR_VERSION=0.16.0
export OPEN_TELEMETRY_COLLECTOR_PLATFORM="darwin_amd64"


##########################################################################################
# PARENT DIRECTORY
# code copied from Tomcat's `catalina.sh`
##########################################################################################
# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

export OPEN_TELEMETRY_COLLECTOR_HOME=$PRGDIR/../.otel/collector/v$OPEN_TELEMETRY_COLLECTOR_VERSION
mkdir -p "$OPEN_TELEMETRY_COLLECTOR_HOME"


##########################################################################################
# DOWNLOAD OPEN TELEMETRY COLLECTOR IF NOT FOUND
# code copied from Maven Wrappers's mvnw`
##########################################################################################
export OPEN_TELEMETRY_COLLECTOR=$OPEN_TELEMETRY_COLLECTOR_HOME/otelcontribcol
if [ -r "$OPEN_TELEMETRY_COLLECTOR" ]; then
    echo "Found $OPEN_TELEMETRY_COLLECTOR"
else
    echo "Couldn't find $OPEN_TELEMETRY_COLLECTOR, downloading it ..."
    OPEN_TELEMETRY_COLLECTOR_URL="https://github.com/open-telemetry/opentelemetry-collector-contrib/releases/download/v$OPEN_TELEMETRY_COLLECTOR_VERSION/otelcontribcol_$OPEN_TELEMETRY_COLLECTOR_PLATFORM"

    if command -v wget > /dev/null; then
        wget "$OPEN_TELEMETRY_COLLECTOR_URL" -O "$OPEN_TELEMETRY_COLLECTOR"
        chmod a+x $OPEN_TELEMETRY_COLLECTOR
    elif command -v curl > /dev/null; then
        curl -o "$OPEN_TELEMETRY_COLLECTOR" "$OPEN_TELEMETRY_COLLECTOR_URL"
        chmod a+x $OPEN_TELEMETRY_COLLECTOR
    else
        echo "FAILURE: OpenTelemetry collector not found and none of curl and wget found"
        exit 1;
    fi
fi

set -x

$OPEN_TELEMETRY_COLLECTOR --config $PRGDIR/opentelemetry-collector-exporter-elastic.yaml