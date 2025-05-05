.DEFAULT_GOAL := start

export OTELCOL_IMG?=otel/opentelemetry-collector-contrib-dev:latest
export ELASTIC_STACK_VERSION?=9.0.0

.PHONY: build
build:
	@env docker compose --file docker-compose.yml build --no-cache

.PHONY: start
start:
	@env docker compose --file docker-compose.yml up --detach --no-recreate

.PHONY: start-elastic
start-elastic:
	@env docker compose --file docker-compose-elastic.yml up --detach --no-recreate

.PHONY: start-elastic-cloud
start-elastic-cloud:
	@env docker compose --file docker-compose-elastic-cloud.yml up --detach --no-recreate

.PHONY: start-grafana
start-grafana:
	@env docker compose --file docker-compose-grafana.yml up --detach --no-recreate

.PHONY: stop
stop:
	@env docker compose --file docker-compose.yml \
		--file docker-compose-grafana.yml \
		--file docker-compose-elastic.yml \
		--file docker-compose-elastic-cloud.yml \
		stop

.PHONY: clean
clean:
	@env docker compose --file docker-compose.yml \
		--file docker-compose-grafana.yml \
		--file docker-compose-elastic.yml \
		--file docker-compose-elastic-cloud.yml \
		down -v --remove-orphans
