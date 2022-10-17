.DEFAULT_GOAL := start

export OTELCOL_IMG?=otel/opentelemetry-collector-contrib-dev:latest
export ELASTIC_STACK_VERSION?=8.4.2

.PHONY: build
build:
	@env docker-compose --file docker-compose.yml build

.PHONY: start
start:
	@env docker-compose --file docker-compose.yml up --detach --no-recreate

.PHONY: stop
stop:
	@env docker-compose --file docker-compose.yml stop

.PHONY: clean
clean:
	@env docker-compose --file docker-compose.yml down -v

.PHONY: start-all  ## Start everything locally
start-all: start start-local-worker

.PHONY: stop-all  ## Stop everything locally
stop-all: stop-local-worker stop

.PHONY: start-local-worker
start-local-worker:  ## Start the local worker
	[ ! -e agent.jar ] && wget --quiet -O agent.jar http://localhost:8080/jnlpJars/agent.jar || true
	{ nohup java -jar agent.jar -jnlpUrl http://localhost:8080/computer/local/jenkins-agent.jnlp > local.log 2>&1 & echo $$! > local.pid; }

.PHONY: stop-local-worker
stop-local-worker:  ## Stop the local worker
	[ -e local.pid ] && ( kill -9 `cat local.pid` ; rm local.pid ) || true