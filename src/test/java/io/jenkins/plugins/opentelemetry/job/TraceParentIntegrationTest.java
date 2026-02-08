package io.jenkins.plugins.opentelemetry.job;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.opentelemetry.api.ReconfigurableOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit4.OpenTelemetryRule;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TraceParentIntegrationTest {

    @Rule
    public OpenTelemetryRule otelTesting = OpenTelemetryRule.create();

    @Mock
    Run run;

    @Mock
    Job job;

    @Mock
    TaskListener listener;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ReconfigurableOpenTelemetry reconfigurableOpenTelemetry;

    private OtelEnvironmentContributor contributor;
    private Tracer tracer;

    @Before
    public void setup() throws Exception {
        this.contributor = new OtelEnvironmentContributor();
        this.tracer = otelTesting.getOpenTelemetry().getTracer("test-tracer");

        // Setup Mock Interactions
        when(run.getParent()).thenReturn(job);
        when(job.getFullName()).thenReturn("test-pipeline-job");

        // Instantiate the Real Service
        OtelEnvironmentContributorService service = new OtelEnvironmentContributorService();

        // Inject 'ReconfigurableOpenTelemetry' into 'Service'
        injectDependencyByType(service, reconfigurableOpenTelemetry, ReconfigurableOpenTelemetry.class);

        // Inject 'Service' into 'Contributor'
        injectDependencyByType(this.contributor, service, OtelEnvironmentContributorService.class);
    }

    @Test
    public void testContextPropagatesToNestedLayer() throws IOException, InterruptedException {
        // Simulate the "Root" Layer
        Span rootSpan = tracer.spanBuilder("root-build").startSpan();
        String rootSpanId = rootSpan.getSpanContext().getSpanId();

        try (Scope rootScope = rootSpan.makeCurrent()) {

            // Simulate a "Nested" Layer
            Span stageSpan = tracer.spanBuilder("stage-layer").startSpan();
            String stageSpanId = stageSpan.getSpanContext().getSpanId();
            String stageTraceId = stageSpan.getSpanContext().getTraceId();

            // ACTIVATE the nested layer
            try (Scope stageScope = stageSpan.makeCurrent()) {

                EnvVars envs = new EnvVars();
                contributor.buildEnvironmentFor(run, envs, listener);

                String traceParent = envs.get("TRACEPARENT");
                assertNotNull("TRACEPARENT variable should be injected", traceParent);

                assertTrue(
                        "Trace context must match the current active stage layer", traceParent.contains(stageSpanId));

                assertFalse("Trace context must NOT match the root build layer", traceParent.contains(rootSpanId));

                assertTrue("Trace ID must match the current context", traceParent.contains(stageTraceId));
            } finally {
                stageSpan.end();
            }
        } finally {
            rootSpan.end();
        }
    }

    private void injectDependencyByType(Object target, Object dependency, Class<?> dependencyType) throws Exception {
        boolean found = false;

        for (Field field : target.getClass().getDeclaredFields()) {
            if (field.getType().isAssignableFrom(dependencyType)) {
                field.setAccessible(true);
                field.set(target, dependency);
                found = true;
                break;
            }
        }
        if (!found) {
            // Check parent class fields
            for (Field field : target.getClass().getSuperclass().getDeclaredFields()) {
                if (field.getType().isAssignableFrom(dependencyType)) {
                    field.setAccessible(true);
                    field.set(target, dependency);
                    found = true;
                    break;
                }
            }
        }
    }
}
