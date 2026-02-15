# AGENTS

Agent instructions for the Jenkins OpenTelemetry Plugin.

## Project Overview

**Artifact**: `io.jenkins.plugins:opentelemetry`  
**Type**: Jenkins HPI Plugin (`.hpi` packaging)  
**Language**: Java 21  
**Build Tool**: Maven  

**Purpose**: Monitor and observe Jenkins with OpenTelemetry. Provides distributed tracing for pipeline executions, HTTP requests, metrics collection, and log storage integration with observability backends (Elastic, Jaeger, Grafana, Dynatrace).

**Note**: Version information for dependencies can be found in `pom.xml`.

## Design Principles

Follow these principles when making changes:

- **KISS (Keep It Simple, Stupid)**: Straightforward implementations without over-engineering
- **TDA (Tell, Don't Ask)**: Objects encapsulate behavior and tell what to do rather than exposing state
- **YAGNI (You Aren't Gonna Need It)**: Only implement features that are actually needed
- **DRY (Don't Repeat Yourself)**: Reuse code through well-defined abstractions
- **Structured Programming**: Clear control flow with minimal complexity

## Setup Commands

### Prerequisites
- Java 21 or later
- Maven 3.8.x or later

### Build
```bash
./mvnw clean install
```

### Run with Jenkins
```bash
./mvnw hpi:run
```
This starts Jenkins on http://localhost:8080/jenkins with the plugin loaded.

### Run Demo Environments
```bash
cd demos
make start-elastic  # Elastic Stack (Elasticsearch, Kibana, APM)
make start-grafana  # Grafana Stack (Tempo, Loki)
make start          # Base stack
make stop           # Stop all containers
```

### Code Formatting with Spotless
```bash
# Check code formatting
./mvnw spotless:check

# Apply code formatting
./mvnw spotless:apply
```

### Static Analysis
```bash
# Run SpotBugs analysis
./mvnw spotbugs:check

# Run Error Prone analysis
./mvnw clean verify -P error-prone-check
```

## Code Style Guidelines

### General Java Conventions
- Use `@NonNull` and `@CheckForNull` annotations from FindBugs
- Never return null; use `Optional` for potentially absent values
- Use `@Extension` annotation for Jenkins extension points
- Mark optional extensions with `@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)`
- Prefer composition over inheritance
- Use interface-based design for extensibility

### Formatting Rules (enforced by Spotless)
- **Indentation**: 4 spaces for Java, 2 spaces for YAML/JSON
- **Line length**: Maximum 120 characters
- **End of line**: LF (Unix style)
- **Charset**: UTF-8
- **Trailing whitespace**: Removed in Java files
- **Final newline**: Not required in most files, required in YAML/JSON/shell scripts
- Always run `./mvnw spotless:apply` before committing

### Static Analysis (SpotBugs/FindBugs)
- All code must pass SpotBugs checks without warnings
- Use FindBugs annotations to document nullability:
  - `@NonNull` - Parameter/return value cannot be null
  - `@CheckForNull` - May return null (prefer Optional instead)
  - `@SuppressFBWarnings` - Only when necessary with justification
- Run `./mvnw spotbugs:check` to verify before committing

### Handler Pattern
All handlers must implement:
```java
@Override
public boolean canCreateSpanBuilder(@NonNull Run<?, ?> run) {
    // Check capability
}

@Override
public int ordinal() {
    return 100; // Lower executes first
}

@Override
public int compareTo(OtherHandler other) {
    if (this.ordinal() == other.ordinal()) {
        return this.getClass().getName().compareTo(other.getClass().getName());
    }
    return Integer.compare(this.ordinal(), other.ordinal());
}
```

### Naming Conventions
- Listener classes end with `Listener` (e.g., `MonitoringRunListener`)
- Handler classes end with `Handler` (e.g., `RunHandler`, `CauseHandler`)
- Backend classes end with `Backend` (e.g., `ElasticBackend`)
- Use semantic attribute names from OpenTelemetry conventions
- Configuration classes use `Configuration` suffix

### TDA (Tell, Don't Ask) Examples
**Bad** (Asking):
```java
if (run.getResult() == Result.FAILURE) {
    span.setAttribute("status", "failed");
}
```

**Good** (Telling):
```java
span.setStatus(StatusCode.ERROR);
```

### Package Organization
- `authentication/` - OTLP authentication mechanisms
- `backend/` - Observability backend integrations
- `computer/` - Jenkins agent monitoring
- `init/` - Plugin initialization
- `job/` - Job and pipeline monitoring
  - `job/cause/` - Build trigger handlers
  - `job/runhandler/` - Run type handlers
  - `job/step/` - Pipeline step handlers
  - `job/log/` - Build log management
- `queue/` - Build queue monitoring
- `security/` - Security event monitoring
- `semconv/` - Semantic conventions
- `servlet/` - HTTP request tracing

## Testing Instructions

### Run All Tests
```bash
./mvnw clean verify
```

### Run Specific Test
```bash
./mvnw test -Dtest=MonitoringRunListenerTest
```

### Run Integration Tests with Testcontainers
```bash
./mvnw verify -pl :opentelemetry -Pintegration-tests
```

### Test Coverage
- Unit tests use Mockito for Jenkins API mocking
- Integration tests use Testcontainers for Elastic/Jaeger
- Pipeline tests use Jenkins test harness
- All handler implementations must have tests
- Tests must verify OpenTelemetry span/metric creation

### Adding Tests
When adding new features:
1. Add unit tests for the component
2. Add integration tests if it involves external systems
3. Verify spans are created with correct attributes
4. Test error cases and edge conditions
5. Run `./mvnw verify` before committing

### Test Utilities
- `ExtendedGitSampleRepoRule` - Git repository testing
- `ElasticStack` - Testcontainers for Elastic
- Configuration as Code test harness

## Pull Request Guidelines

### Before Opening PR
1. Format code: `./mvnw spotless:apply`
2. Check formatting: `./mvnw spotless:check`
3. Run static analysis: `./mvnw spotbugs:check`
4. Run full test suite: `./mvnw clean verify`
5. Run Error Prone: `./mvnw clean verify -P error-prone-check`
6. Verify all tests pass
7. Update documentation if adding features
8. Add changelog entry if applicable

### PR Title Format
```
[JENKINS-XXXXX] Brief description of change
```
Or for minor changes without JIRA:
```
Fix typo in MonitoringRunListener
```

### PR Description Must Include
- What problem does this solve?
- How to test the change
- Any breaking changes
- Screenshots for UI changes
- Link to JIRA issue if applicable

### Commit Messages
Follow [conventional commits](https://www.conventionalcommits.org/):
```
feat: add support for custom trace attributes
fix: correct span timing for parallel branches
docs: update AGENTS.md with testing instructions
test: add integration tests for Grafana backend
```

### Code Review Expectations
- All handlers must follow the ordinal pattern
- Code must pass Spotless, SpotBugs, and Error Prone checks
- Proper use of `@NonNull` and `@CheckForNull` annotations
- No new dependencies without justification
- Security considerations documented
- Performance impact assessed
- Backward compatibility maintained

## Architecture Overview

Event-driven architecture using Jenkins extension points and OpenTelemetry SDK.

### High-Level Flow
```mermaid
flowchart TB
    Jenkins["Jenkins Core"] --> Listeners["Plugin Listeners"]
    Listeners --> Handlers["Extensible Handlers"]
    Handlers --> SDK["OpenTelemetry SDK"]
    SDK --> Collector["OTLP Collector"]
    Collector --> Backends["Observability Backends"]
```

### Key Extension Points
1. **RunListener** → `MonitoringRunListener` - Build lifecycle events
2. **GraphListener** → `GraphListenerAdapterToPipelineListener` - Pipeline events  
3. **ServletFilter** → `TraceContextServletFilter` - HTTP tracing
4. **ComputerListener** → `MonitoringComputerListener` - Agent events
5. **QueueListener** → `MonitoringQueueListener` - Queue events
6. **SecurityListener** → `AuditingSecurityListener` - Security events

## Core Components for Development

### Configuration Entry Points
1. **`JenkinsOpenTelemetryPluginConfiguration`** - Global config, OTLP endpoint, authentication
2. **`JenkinsControllerOpenTelemetry`** - SDK lifecycle singleton
3. **`OpenTelemetryConfiguration`** - Properties, environment variables

### Authentication Implementations  
Extend `OtlpAuthentication`: `NoAuthentication`, `BearerTokenAuthentication`, `HeaderAuthentication`

### Observability Backends
Extend `ObservabilityBackend`: `ElasticBackend`, `JaegerBackend`, `ZipkinBackend`, `GrafanaBackend`, `DynatraceBackend`

### Monitoring Components
- **Job Monitoring**: `MonitoringRunListener` delegates to `RunHandler` implementations
- **Pipeline Monitoring**: `MonitoringPipelineListener` implements `PipelineListener` interface
- **HTTP Tracing**: `TraceContextServletFilter`, `StaplerInstrumentationServletFilter`  
- **Agent Monitoring**: `MonitoringComputerListener`, `MonitoringCloudListener`
- **Queue Monitoring**: `MonitoringQueueListener`
- **Security Monitoring**: `AuditingSecurityListener`
- **Log Management**: `OtelLogSenderBuildListener`, `OtelLogOutputStream`

## Extension Points & Handlers

### Handler Pattern Requirements
All handlers implement:
- `isSupported()` / `canCreateSpanBuilder()` - Check if handler applies
- `ordinal()` - Execution order (lower runs first)
- `configure(ConfigProperties)` - Optional configuration
- `compareTo()` - For deterministic ordering

### Handler Types

**RunHandler** - Different run types (Pipeline, Freestyle, Maven)
```java
@Extension(optional = true)
public class MyRunHandler implements RunHandler {
    @Override
    public boolean canCreateSpanBuilder(@NonNull Run<?, ?> run) {
        return run instanceof MyRunType;
    }
    
    @Override
    public SpanBuilder createSpanBuilder(@NonNull Run<?, ?> run, @NonNull Tracer tracer) {
        return tracer.spanBuilder("jenkins.run.my_type")
            .setAttribute("my.attribute", value);
    }
    
    @Override
    public int ordinal() { return 100; }
}
```

**StepHandler** - Pipeline steps (sh, bat, git)
```java
@Extension
public class MyStepHandler implements StepHandler {
    @Override
    public boolean canCreateSpanBuilder(@NonNull FlowNode node, @NonNull WorkflowRun run) {
        return node instanceof StepAtomNode 
            && ((StepAtomNode) node).getDescriptor() instanceof MyStepDescriptor;
    }
    
    @Override
    public SpanBuilder createSpanBuilder(@NonNull FlowNode node, @NonNull WorkflowRun run, @NonNull Tracer tracer) {
        return tracer.spanBuilder("jenkins.step.my_step");
    }
}
```

**CauseHandler** - Build triggers (GitHub, GitLab, User)
```java
@Extension(optional = true)
public class MyCauseHandler implements CauseHandler {
    @Override
    public boolean isSupported(@NonNull Cause cause) {
        return cause instanceof MyCause;
    }
    
    @Override
    public String getStructuredDescription(@NonNull Cause cause) {
        return "MyCause:" + ((MyCause) cause).getId();
    }
    
    @Override
    public int ordinal() { return 100; }
}
```

## Key Dependencies

### Core (bundled with plugin)
- `io.opentelemetry:opentelemetry-bom` - OpenTelemetry API/SDK
- `io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom` - Instrumentation
- `io.opentelemetry.semconv:opentelemetry-semconv` - Semantic conventions
- `co.elastic.clients:elasticsearch-java` - Elasticsearch client

### Jenkins Plugins (required)
- `io.jenkins.plugins:opentelemetry-api` - OpenTelemetry wrapper
- `org.jenkins-ci.plugins.workflow:workflow-*` - Pipeline support
- `org.jenkins-ci.plugins:credentials` - Credential management

### Jenkins Plugins (optional)
- `org.jenkins-ci.plugins:git` - Git SCM
- `org.jenkins-ci.plugins:github-branch-source` - GitHub
- `org.jenkins-ci.plugins:gitlab-plugin` - GitLab
- `org.jenkins-ci.plugins:bitbucket` - Bitbucket
- `org.jenkins-ci.plugins:job-dsl` - Job DSL

### Adding Dependencies
1. Check if already provided by parent POM
2. Use dependency management for version control 
3. Exclude transitive dependencies that conflict with Jenkins
4. Test with minimal Jenkins version (see `pom.xml` for baseline)

## Package Structure

```
io.jenkins.plugins.opentelemetry/
├── authentication/          # OTLP authentication (NoAuth, Bearer, Header)
├── backend/                # Backend integrations (Elastic, Jaeger, Grafana, etc.)
│   ├── custom/            # Custom backend implementations
│   ├── elastic/           # Elasticsearch log retrieval
│   └── grafana/           # Loki log retrieval
├── computer/               # Agent lifecycle monitoring
├── init/                   # Plugin initialization, JUL bridging
├── jenkins/                # Jenkins core integration
├── job/                    # Job and pipeline monitoring
│   ├── cause/             # Build trigger handlers (GitHub, GitLab, User, etc.)
│   ├── jenkins/           # Pipeline listeners and adapters
│   ├── log/               # Log capture and export
│   ├── runhandler/        # Run type handlers (Pipeline, Freestyle, etc.)
│   └── step/              # Pipeline step handlers (sh, bat, git, etc.)
├── queue/                  # Build queue monitoring
├── security/               # Security event monitoring
├── semconv/                # Semantic conventions (attributes, metrics)
└── servlet/                # HTTP request tracing filters
```

## Data Flow

### Build Execution Trace

```mermaid
sequenceDiagram
    participant Queue as Job Queue
    participant QueueListener as MonitoringQueueListener
    participant RunListener as MonitoringRunListener
    participant RunHandler as RunHandler
    participant PipelineListener as MonitoringPipelineListener
    participant GraphAdapter as GraphListenerAdapter
    participant StepHandler as StepHandler
    participant OTLP as OTLP Backend

    Queue->>QueueListener: Job queued
    QueueListener->>RunListener: Job started
    RunListener->>RunHandler: onStarted()
    RunHandler->>OTLP: Create root span
    
    RunListener->>PipelineListener: onStartPipeline()
    
    loop For each stage
        GraphAdapter->>PipelineListener: Detect stage node
        PipelineListener->>OTLP: onStartStageStep()<br/>Create child span
        
        loop For each step
            PipelineListener->>StepHandler: canCreateSpanBuilder()
            StepHandler->>StepHandler: createSpanBuilder()
            StepHandler->>OTLP: Create child span
        end
        
        PipelineListener->>OTLP: onEndStageStep()<br/>Close stage span
    end
    
    RunListener->>RunHandler: onCompleted()
    RunHandler->>OTLP: Close root span<br/>Record metrics
```

### HTTP Request Trace

```mermaid
sequenceDiagram
    participant Client as HTTP Client
    participant TraceFilter as TraceContextServletFilter
    participant StaplerFilter as StaplerInstrumentationServletFilter
    participant Jenkins as Jenkins Handler
    participant OTLP as OTLP Backend

    Client->>TraceFilter: HTTP Request
    TraceFilter->>TraceFilter: Extract trace context<br/>(W3C propagation)
    TraceFilter->>StaplerFilter: Forward request
    StaplerFilter->>OTLP: Create span
    StaplerFilter->>Jenkins: Process request
    Jenkins-->>StaplerFilter: Response
    StaplerFilter->>OTLP: Close span<br/>(with status code)
    StaplerFilter-->>Client: HTTP Response
```

### Log Export

```mermaid
sequenceDiagram
    participant Step as Pipeline Step
    participant LogStream as OtelLogOutputStream
    participant Logger as OTel Logger
    participant Context as Trace Context
    participant OTLP as OTLP Backend
    participant Mirror as TeeBuildListener
    participant Jenkins as Jenkins Storage

    Step->>LogStream: Write log output
    LogStream->>Logger: Convert to LogRecord
    LogStream->>Context: Attach trace context
    Logger->>OTLP: Export log
    
    alt Log Mirroring Enabled
        LogStream->>Mirror: Mirror log
        Mirror->>Jenkins: Store in Jenkins
    end
```

### Typical Development Flow

1. **Job queued** → `MonitoringQueueListener` captures queue event
2. **Job started** → `MonitoringRunListener.onStarted()` creates root span
3. **RunHandler** creates span builder for specific run type
4. **Pipeline starts** → `MonitoringPipelineListener.onStartPipeline()`
5. **For each stage**: 
   - `GraphListenerAdapterToPipelineListener` detects stage node
   - `onStartStageStep()` creates child span
   - **For each step**: StepHandler creates span if applicable
   - `onEndStageStep()` closes stage span
6. **Build completes** → `MonitoringRunListener.onCompleted()` closes root span
7. **Metrics recorded** (duration, result, queue time)

## Configuration Properties

OpenTelemetry configuration (set in Jenkins UI or JCasC):

```properties
otel.exporter.otlp.endpoint=http://localhost:4317
otel.exporter.otlp.protocol=grpc  # or http/protobuf
otel.exporter.otlp.headers=Authorization=Bearer token123
otel.instrumentation.jenkins.agent.enabled=true
otel.instrumentation.jenkins.remoting.enabled=false
```

## Common Tasks

### Add a New Backend
1. Extend `ObservabilityBackend` in `backend/`
2. Implement `getTraceVisualisationUrlTemplate()` for trace viewing
3. Optionally implement log storage retrieval
4. Add `@Extension` and `@Symbol` annotations
5. Add configuration UI in `src/main/resources/`
6. Test with demo environment

### Add a New RunHandler
1. Create class implementing `RunHandler` in `job/runhandler/`
2. Implement `canCreateSpanBuilder()` to identify run type
3. Implement `createSpanBuilder()` to create span with attributes
4. Set appropriate `ordinal()` for execution order
5. Add `@Extension(optional = true)` if depends on optional plugin
6. Add tests verifying span creation

### Add a New StepHandler
1. Create class implementing `StepHandler` in `job/step/`
2. Implement `canCreateSpanBuilder()` to match step type
3. Implement `createSpanBuilder()` to create span
4. Override `afterSpanCreated()` if needed for additional attributes
5. Add integration tests with pipeline

### Troubleshoot Missing Spans
1. Check handler `ordinal()` - lower values execute first
2. Verify `canCreateSpanBuilder()` returns true for your case
3. Add debug logging in handler
4. Check OpenTelemetry SDK is initialized: `GlobalOpenTelemetry.get()`
5. Verify OTLP exporter configuration

## Performance Considerations

- Handlers initialized lazily on first use
- OpenTelemetry batches exports asynchronously
- Bounded queues prevent memory exhaustion
- Sampling configurable via OTLP configuration
- Agent instrumentation and remoting are opt-in

## Security Considerations

- Store sensitive tokens in Jenkins credentials, not plain text
- Use pluggable authentication for OTLP endpoints
- Security listener tracks authentication events
- Filter sensitive data from spans/logs
- Use HTTPS for OTLP communication

## References

- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [Jenkins Plugin Development](https://www.jenkins.io/doc/developer/)
- [Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [OTLP Protocol](https://opentelemetry.io/docs/specs/otlp/)

## Contributing

See `README.md` for contribution guidelines. Follow the established patterns:

1. Use handler pattern for extensibility
2. Follow semantic conventions
3. Add tests for new functionality
4. Document configuration options
5. Update relevant backend integrations
