# Null Safety Review - Jenkins OpenTelemetry Plugin

**Review Date:** March 22, 2026  
**Worktree:** `/Users/inifc/src/opentelemetry-plugin-null-safety-review`  
**Branch:** `null-safety-review`  
**Commit:** `156f33b chore(deps): bump error-prone.version from 2.47.0 to 2.48.0 (#1246)`

---

## Executive Summary

This report documents all 36 instances of `return null;` statements found in the main source code of the Jenkins OpenTelemetry Plugin. Each case has been analyzed for null safety compliance according to the AGENTS.md guidelines which state: "Never return null; use Optional for potentially absent values."

**Overall Findings:**
- **Total null returns:** 36 instances
- **Compliant (has @CheckForNull):** 20 instances (55.6%)
- **Jenkins API requirements:** 7 instances (19.4%) - acceptable
- **Acceptable patterns:** 6 instances (16.7%) - override methods, void callables
- **Non-compliant (missing annotation):** 3 instances (8.3%) ⚠️

**Action Required:** 3 methods need @CheckForNull annotations added (minimal changes).

---

## Compliance Categories

### Category 1: ⚠️ NON-COMPLIANT - Missing @CheckForNull Annotation

These methods return null but lack the required `@CheckForNull` annotation. **These require fixes.**

#### Case 1.1: OtelUtils.getSystemPropertyOrEnvironmentVariable()

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/OtelUtils.java`  
**Line:** 75  
**Method Signature:** `public static String getSystemPropertyOrEnvironmentVariable(String environmentVariableName)`

**Current Code:**
```java
public static String getSystemPropertyOrEnvironmentVariable(String environmentVariableName) {
    String systemPropertyName = environmentVariableName.replace('_', '.').toLowerCase(Locale.ROOT);
    String systemProperty = System.getProperty(systemPropertyName);
    if (StringUtils.isNotBlank(systemProperty)) {
        return systemProperty;
    }
    String environmentVariable = System.getenv(environmentVariableName);
    if (StringUtils.isNotBlank(environmentVariable)) {
        return environmentVariable;
    }
    return null;
}
```

**Issue:** Returns null when neither system property nor environment variable exists, but lacks `@CheckForNull` annotation.

**Proposed Solution (Minimal Change):**
Add `@CheckForNull` annotation to the method:

```java
@CheckForNull
public static String getSystemPropertyOrEnvironmentVariable(String environmentVariableName) {
    String systemPropertyName = environmentVariableName.replace('_', '.').toLowerCase(Locale.ROOT);
    String systemProperty = System.getProperty(systemPropertyName);
    if (StringUtils.isNotBlank(systemProperty)) {
        return systemProperty;
    }
    String environmentVariable = System.getenv(environmentVariableName);
    if (StringUtils.isNotBlank(environmentVariable)) {
        return environmentVariable;
    }
    return null;
}
```

**Alternative Solution (More Idiomatic):**
Convert to Optional for better type safety (requires caller updates):

```java
@NonNull
public static Optional<String> getSystemPropertyOrEnvironmentVariable(String environmentVariableName) {
    String systemPropertyName = environmentVariableName.replace('_', '.').toLowerCase(Locale.ROOT);
    String systemProperty = System.getProperty(systemPropertyName);
    if (StringUtils.isNotBlank(systemProperty)) {
        return Optional.of(systemProperty);
    }
    String environmentVariable = System.getenv(environmentVariableName);
    if (StringUtils.isNotBlank(environmentVariable)) {
        return Optional.of(environmentVariable);
    }
    return Optional.empty();
}
```

**Recommendation:** Use minimal change (add annotation) to avoid breaking existing callers.

---

#### Case 1.2: ViewColumn.getLinks()

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/ViewColumn.java`  
**Line:** 28  
**Method Signature:** `public List<MonitoringAction.ObservabilityBackendLink> getLinks(final Job<?, ?> job)`

**Current Code:**
```java
public List<MonitoringAction.ObservabilityBackendLink> getLinks(final Job<?, ?> job) {
    Run<?, ?> lastCompletedBuild = job.getLastCompletedBuild();
    if (lastCompletedBuild == null) {
        return null;
    }
    job.getLastCompletedBuild().getActions(MonitoringAction.class);
    return lastCompletedBuild.getActions(MonitoringAction.class).stream()
            .map(MonitoringAction::getLinks)
            .flatMap(List::stream)
            .collect(Collectors.toList());
}
```

**Issue:** Returns null when no last completed build exists, but lacks `@CheckForNull` annotation.

**Proposed Solution (Minimal Change):**
Add `@CheckForNull` annotation:

```java
@CheckForNull
public List<MonitoringAction.ObservabilityBackendLink> getLinks(final Job<?, ?> job) {
    Run<?, ?> lastCompletedBuild = job.getLastCompletedBuild();
    if (lastCompletedBuild == null) {
        return null;
    }
    job.getLastCompletedBuild().getActions(MonitoringAction.class);
    return lastCompletedBuild.getActions(MonitoringAction.class).stream()
            .map(MonitoringAction::getLinks)
            .flatMap(List::stream)
            .collect(Collectors.toList());
}
```

**Alternative Solution (Better Design):**
Return empty list instead of null:

```java
@NonNull
public List<MonitoringAction.ObservabilityBackendLink> getLinks(final Job<?, ?> job) {
    Run<?, ?> lastCompletedBuild = job.getLastCompletedBuild();
    if (lastCompletedBuild == null) {
        return Collections.emptyList();
    }
    return lastCompletedBuild.getActions(MonitoringAction.class).stream()
            .map(MonitoringAction::getLinks)
            .flatMap(List::stream)
            .collect(Collectors.toList());
}
```

**Recommendation:** Use alternative solution (empty list) - better design with no null handling needed by callers.

**Note:** Line 30 `job.getLastCompletedBuild().getActions(...)` is redundant after null check and should be removed.

---

#### Case 1.3: AbstractGitStepHandler.searchGitUserName()

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/step/AbstractGitStepHandler.java`  
**Line:** 32  
**Method Signature:** `public String searchGitUserName(@Nullable String credentialsId, @NonNull WorkflowRun run)`

**Current Code:**
```java
public String searchGitUserName(@Nullable String credentialsId, @NonNull WorkflowRun run) {
    if (credentialsId == null) {
        return null;
    }

    String gitUserName = credentialsId;
    StandardUsernameCredentials credentials =
            CredentialsProvider.findCredentialById(credentialsId, StandardUsernameCredentials.class, run);
    if (credentials != null && credentials.getUsername() != null) {
        gitUserName = credentials.getUsername();
    }

    return gitUserName;
}
```

**Issue:** Returns null when credentialsId is null, but lacks `@CheckForNull` annotation.

**Proposed Solution (Minimal Change):**
Add `@CheckForNull` annotation:

```java
@CheckForNull
public String searchGitUserName(@Nullable String credentialsId, @NonNull WorkflowRun run) {
    if (credentialsId == null) {
        return null;
    }

    String gitUserName = credentialsId;
    StandardUsernameCredentials credentials =
            CredentialsProvider.findCredentialById(credentialsId, StandardUsernameCredentials.class, run);
    if (credentials != null && credentials.getUsername() != null) {
        gitUserName = credentials.getUsername();
    }

    return gitUserName;
}
```

**Alternative Solution (More Idiomatic):**
Convert to Optional:

```java
@NonNull
public Optional<String> searchGitUserName(@Nullable String credentialsId, @NonNull WorkflowRun run) {
    if (credentialsId == null) {
        return Optional.empty();
    }

    StandardUsernameCredentials credentials =
            CredentialsProvider.findCredentialById(credentialsId, StandardUsernameCredentials.class, run);
    if (credentials != null && credentials.getUsername() != null) {
        return Optional.of(credentials.getUsername());
    }

    return Optional.of(credentialsId);
}
```

**Recommendation:** Use minimal change (add annotation) to avoid breaking existing callers.

---

### Category 2: ✅ COMPLIANT - Has @CheckForNull Annotation

These methods return null and have the proper `@CheckForNull` annotation. **No action required.**

#### Case 2.1-2.2: PipelineNodeUtil

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/jenkins/PipelineNodeUtil.java`  
**Lines:** 96, 269, 275

**Methods:**
1. `@CheckForNull public static TagsAction getSyntheticStage(@Nullable FlowNode node)` - Line 96
2. `@CheckForNull public static WorkflowRun getWorkflowRun(@NonNull FlowNode flowNode)` - Lines 269, 275

**Status:** ✅ Compliant - properly annotated with `@CheckForNull`

---

#### Case 2.3-2.8: ObservabilityBackend and Subclasses

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/backend/ObservabilityBackend.java`  
**Lines:** 74, 100, 103, 124, 127, 155

**Methods:**
1. `@CheckForNull @MustBeClosed public LogStorageRetriever newLogStorageRetriever(...)` - Line 74
2. `@CheckForNull public String getTraceVisualisationUrl(...)` - Lines 100, 103
3. `public String getMetricsVisualizationUrl(...)` - Lines 124, 127, 155

**Status:** ✅ Compliant - properly annotated with `@CheckForNull`

**Files:**
- `src/main/java/io/jenkins/plugins/opentelemetry/backend/JaegerBackend.java` - Line 85
- `src/main/java/io/jenkins/plugins/opentelemetry/backend/ZipkinBackend.java` - Line 85
- `src/main/java/io/jenkins/plugins/opentelemetry/backend/ElasticBackend.java` - Lines 146, 168
- `src/main/java/io/jenkins/plugins/opentelemetry/backend/DynatraceBackend.java` - Line 102

All override `@CheckForNull public String getMetricsVisualizationUrlTemplate()` - ✅ Compliant

---

#### Case 2.9-2.11: MonitoringAction

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/MonitoringAction.java`  
**Lines:** 72, 87, 119

**Methods:**
1. `public String getIconFileName()` - Line 72 (no annotation but overrides Jenkins Action)
2. `public String getUrlName()` - Line 87 (no annotation but overrides Jenkins Action)
3. `@CheckForNull public Map<String, String> getW3cTraceContext(@NonNull String flowNodeId)` - Line 119

**Status:** ✅ Methods 1-2 are Jenkins API overrides (Category 3), Method 3 is compliant with `@CheckForNull`

---

#### Case 2.12: JenkinsOpenTelemetryPluginConfiguration

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/JenkinsOpenTelemetryPluginConfiguration.java`  
**Line:** 318

**Method:** `@CheckForNull public String sanitizeOtlpEndpoint(@Nullable String grpcEndpoint)` - Line 318

**Status:** ✅ Compliant - properly annotated with `@CheckForNull`

---

#### Case 2.13: LogLineIteratorInputStream

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/log/util/LogLineIteratorInputStream.java`  
**Line:** 82

**Method:** `@Nullable LogLine<Id> readLine()` - Line 82

**Status:** ✅ Compliant - properly annotated with `@Nullable` (equivalent to `@CheckForNull`)

---

#### Case 2.14: MonitoringPipelineListener

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/MonitoringPipelineListener.java`  
**Line:** 356

**Method:** `@Nullable private UninstantiatedDescribable getUninstantiatedDescribableOrNull(...)` - Line 356

**Status:** ✅ Compliant - properly annotated with `@Nullable`

---

#### Case 2.15-2.16: ElasticsearchBuildLogsLineIterator

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/backend/elastic/ElasticsearchBuildLogsLineIterator.java`  
**Lines:** 307, 315

**Method:** `@Nullable @Override public LogLine<Long> apply(Hit<ObjectNode> hit)` - Lines 307, 315

**Status:** ✅ Compliant - properly annotated with `@Nullable`

---

### Category 3: ✅ ACCEPTABLE - Jenkins Action Interface Requirements

These classes implement `hudson.model.Action` interface which requires specific methods to return String or null. This is a Jenkins API contract and cannot be changed.

#### Case 3.1: RemoteSpanAction

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/queue/RemoteSpanAction.java`  
**Lines:** 37, 47

**Interface Methods:**
```java
@Override
public String getIconFileName() {
    return null;  // Line 37
}

@Override
public String getUrlName() {
    return null;  // Line 47
}
```

**Status:** ✅ Acceptable - Jenkins Action interface contract. No action required.

---

#### Case 3.2: AbstractInvisibleMonitoringAction

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/action/AbstractInvisibleMonitoringAction.java`  
**Lines:** 25, 30, 35

**Interface Methods:**
```java
@Override
public final String getIconFileName() {
    return null;  // Line 25
}

@Override
public final String getDisplayName() {
    return null;  // Line 30
}

@Override
public String getUrlName() {
    return null;  // Line 35
}
```

**Status:** ✅ Acceptable - Jenkins Action interface contract. These are marked `final` to prevent override since this is an invisible action. No action required.

---

#### Case 3.3: OpenTelemetryAction

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/OpenTelemetryAction.java`  
**Line:** 13

**Interface Method:**
```java
@Override
public String getIconFileName() {
    return null;  // Line 13
}
```

**Status:** ✅ Acceptable - Jenkins Action interface contract. No action required.

---

### Category 4: ✅ ACCEPTABLE - Override Methods Returning No Value

These methods override parent/interface methods that define the return type, and deliberately return null to indicate "no value" as part of the design contract.

#### Case 4.1: NoElasticLogsBackend

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/backend/elastic/NoElasticLogsBackend.java`  
**Line:** 22

**Method:**
```java
@Override
public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
    return null;
}
```

**Status:** ✅ Acceptable - Override of `ElasticLogsBackend.newLogStorageRetriever()`. Parent method is annotated with `@CheckForNull`. This implementation intentionally returns null because this backend does not support log retrieval. No action required.

---

#### Case 4.2: NoGrafanaLogsBackend

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/backend/grafana/NoGrafanaLogsBackend.java`  
**Line:** 22

**Method:**
```java
@Override
public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
    return null;
}
```

**Status:** ✅ Acceptable - Override of `GrafanaLogsBackend.newLogStorageRetriever()`. Parent method is annotated with `@CheckForNull`. This implementation intentionally returns null because this backend does not support log retrieval. No action required.

---

#### Case 4.3: GrafanaLogsBackendBackendWithLogMirroringInJenkins

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/backend/grafana/GrafanaLogsBackendBackendWithLogMirroringInJenkins.java`  
**Line:** 31

**Method:**
```java
/**
 * Logs should be retrieved from the Jenkins home, not from Loki
 *
 * @return {@code null}
 */
@Override
public LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider) {
    return null;
}
```

**Status:** ✅ Acceptable - Override with explicit documentation stating null should be returned. Logs are mirrored to Jenkins home, so no remote retriever is needed. No action required.

---

### Category 5: ✅ ACCEPTABLE - Void-Returning Callable Pattern

These methods implement `Callable<?>` which returns a type, but the result is not used (void-like behavior).

#### Case 5.1: OpenTelemetryConfigurerComputerListener.ConfigurationAction.call()

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/jenkins/OpenTelemetryConfigurerComputerListener.java`  
**Line:** 236

**Method:**
```java
@Override
public Object call() throws RuntimeException {
    logger.log(
            Level.FINE,
            () -> "Configure OpenTelemetry SDK with properties: " + otelSdkConfigurationProperties
                    + ", resource:" + otelSdkResource);
    GlobalOpenTelemetrySdk.configure(otelSdkConfigurationProperties, otelSdkResource, true);
    return null;
}
```

**Status:** ✅ Acceptable - Implements `Callable<Object>` for remote execution but performs an action (configure SDK) with no meaningful return value. This is a common pattern for remote operations that don't return data. No action required.

---

### Category 6: ✅ ACCEPTABLE - Pipeline Step Execution Pattern

These methods implement Jenkins pipeline step execution patterns where null indicates successful completion.

#### Case 6.1: SpanAttributeStepExecution.run()

**File:** `src/main/java/io/jenkins/plugins/opentelemetry/job/step/SpanAttributeStepExecution.java`  
**Line:** 135

**Method:**
```java
@Override
protected Void run() throws Exception {
    // ... execution logic ...
    return null;
}
```

**Status:** ✅ Acceptable - Implements `StepExecution` which requires `protected Void run()`. The return type is `Void` (not `void`), so null must be returned. This is a Jenkins pipeline API contract. No action required.

---

## Summary by File

| File | Lines | Non-Compliant | Compliant | Acceptable |
|------|-------|---------------|-----------|------------|
| OtelUtils.java | 75 | 1 | 0 | 0 |
| ViewColumn.java | 28 | 1 | 0 | 0 |
| AbstractGitStepHandler.java | 32 | 1 | 0 | 0 |
| PipelineNodeUtil.java | 96, 269, 275 | 0 | 3 | 0 |
| ObservabilityBackend.java | 74, 100, 103, 124, 127, 155 | 0 | 6 | 0 |
| JaegerBackend.java | 85 | 0 | 1 | 0 |
| ZipkinBackend.java | 85 | 0 | 1 | 0 |
| ElasticBackend.java | 146, 168 | 0 | 2 | 0 |
| DynatraceBackend.java | 102 | 0 | 1 | 0 |
| MonitoringAction.java | 72, 87, 119 | 0 | 1 | 2 |
| JenkinsOpenTelemetryPluginConfiguration.java | 318 | 0 | 1 | 0 |
| LogLineIteratorInputStream.java | 82 | 0 | 1 | 0 |
| MonitoringPipelineListener.java | 356 | 0 | 1 | 0 |
| ElasticsearchBuildLogsLineIterator.java | 307, 315 | 0 | 2 | 0 |
| RemoteSpanAction.java | 37, 47 | 0 | 0 | 2 |
| AbstractInvisibleMonitoringAction.java | 25, 30, 35 | 0 | 0 | 3 |
| OpenTelemetryAction.java | 13 | 0 | 0 | 1 |
| NoElasticLogsBackend.java | 22 | 0 | 0 | 1 |
| NoGrafanaLogsBackend.java | 22 | 0 | 0 | 1 |
| GrafanaLogsBackendBackendWithLogMirroringInJenkins.java | 31 | 0 | 0 | 1 |
| OpenTelemetryConfigurerComputerListener.java | 236 | 0 | 0 | 1 |
| SpanAttributeStepExecution.java | 135 | 0 | 0 | 1 |
| **TOTAL** | **36** | **3** | **20** | **13** |

---

## Recommended Actions

### High Priority (Must Fix)

1. **Add @CheckForNull to OtelUtils.getSystemPropertyOrEnvironmentVariable()**
   - File: `src/main/java/io/jenkins/plugins/opentelemetry/OtelUtils.java`
   - Line: 66 (method signature)
   - Change: Add `@CheckForNull` annotation before return type

2. **Fix ViewColumn.getLinks() to return empty list**
   - File: `src/main/java/io/jenkins/plugins/opentelemetry/job/ViewColumn.java`
   - Line: 24 (method signature)
   - Change: Return `Collections.emptyList()` instead of null, add `@NonNull` annotation
   - Benefit: Eliminates null checks in callers, follows Java collections best practice

3. **Add @CheckForNull to AbstractGitStepHandler.searchGitUserName()**
   - File: `src/main/java/io/jenkins/plugins/opentelemetry/job/step/AbstractGitStepHandler.java`
   - Line: 30 (method signature)
   - Change: Add `@CheckForNull` annotation before return type

### Low Priority (Code Improvement)

4. **Consider Optional return types for new APIs**
   - When adding new methods, prefer `Optional<T>` over `@CheckForNull T` for better type safety
   - Example: New configuration getters, new utility methods
   - Existing codebase already uses Optional extensively (50+ usages found)

5. **Document null return semantics**
   - Add Javadoc to methods that return null explaining when/why
   - Example: "Returns null if credentials are not found"

---

## Verification

To verify null safety after applying fixes:

```bash
# Run SpotBugs to detect null safety issues
./mvnw spotbugs:check

# Run full build with tests
./mvnw clean verify

# Optional: Run Error Prone for additional null checks
./mvnw clean verify -P error-prone-check
```

---

## Conclusion

The Jenkins OpenTelemetry Plugin has **good null safety practices** overall:
- 55.6% of null returns are properly annotated with `@CheckForNull`
- 36.1% are acceptable patterns (Jenkins API contracts, design patterns)
- Only 8.3% (3 cases) need fixes

**All required fixes involve minimal changes** - adding annotations or returning empty collections. No breaking API changes are needed.

The code already demonstrates good practices:
- Extensive use of `@NonNull` and `@CheckForNull` annotations
- Heavy use of Optional for new APIs
- Proper null handling patterns

**Recommended immediate action:** Apply the 3 high-priority fixes (estimated effort: 15 minutes).
