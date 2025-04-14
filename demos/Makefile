.DEFAULT_GOAL := start

export OTELCOL_IMG?=otel/opentelemetry-collector-contrib-dev:latest
export ELASTIC_STACK_VERSION?=9.0.0

.PHONY: build
build:
	@env docker compose --file docker-compose.yml build

.PHONY: start
start:
	@env docker compose --file docker-compose.yml up --detach --no-recreate

.PHONY: stop
stop:
	@env docker compose --file docker-compose.yml stop

.PHONY: clean
clean:
	@env docker compose --file docker-compose.yml down -v
