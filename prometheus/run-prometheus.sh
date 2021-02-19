#!/usr/bin/env bash

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

  export PROMETHEUS_STORAGE_PATH=$PRGDIR/../.prometheus-otel/
  mkdir -p "$PROMETHEUS_STORAGE_PATH"

  prometheus --config.file "$PRGDIR/prometheus.yml" --web.listen-address=127.0.0.1:9090 --storage.tsdb.path "$PROMETHEUS_STORAGE_PATH"
