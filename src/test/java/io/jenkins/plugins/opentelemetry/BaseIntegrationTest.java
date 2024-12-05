/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.github.rutledgepaulv.prune.Tree;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.OTelEnvironmentVariablesConventions;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import jenkins.plugins.git.ExtendedGitSampleRepoRule;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verify;
import static java.util.Optional.*;
import static org.junit.Assert.fail;

public class BaseIntegrationTest {
    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    static {
        OpenTelemetryConfiguration.TESTING_INMEMORY_MODE = true;
        OtelTraceService.STRICT_MODE = true;
        GitSCM.ALLOW_LOCAL_CHECKOUT = true;
    }

    public final static AtomicInteger jobNameSuffix = new AtomicInteger();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public static JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();

    @Rule
    public ExtendedGitSampleRepoRule sampleRepo = new ExtendedGitSampleRepoRule();

    static JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
        jenkinsRule.waitUntilNoActivity();
        InMemoryMetricExporterProvider.LAST_CREATED_INSTANCE.reset();
        InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.reset();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        LOGGER.log(Level.INFO, "beforeClass()");
        LOGGER.log(Level.INFO, "Wait for jenkins to start...");
        jenkinsRule.waitUntilNoActivity();
        LOGGER.log(Level.INFO, "Jenkins started");


        ExtensionList<JenkinsControllerOpenTelemetry> jenkinsOpenTelemetries = jenkinsRule.getInstance().getExtensionList(JenkinsControllerOpenTelemetry.class);
        verify(jenkinsOpenTelemetries.size() == 1, "Number of jenkinsControllerOpenTelemetrys: %s", jenkinsOpenTelemetries.size());
        jenkinsControllerOpenTelemetry = jenkinsOpenTelemetries.get(0);

        // verify(jenkinsControllerOpenTelemetry.openTelemetry == null, "JenkinsControllerOpenTelemetry has already been configured");
        OpenTelemetryConfiguration.TESTING_INMEMORY_MODE = true;
        try {
            OpenTelemetryConfiguration configuration = new OpenTelemetryConfiguration(
                of("http://localhost:4317"), empty(),
                empty(),
                empty(), empty(),
                empty(), empty(), empty(),
                Collections.emptyMap());

            LOGGER.log(Level.INFO, "Initialize OTel with configuration " + configuration.toOpenTelemetryProperties());
            jenkinsControllerOpenTelemetry.initialize(configuration);
        } catch (RuntimeException e) {
            LOGGER.log(Level.INFO, "Exception initializing OTel plugin", e);
            throw e;
        } catch (Error e) {
            LOGGER.log(Level.INFO, "Error initializing OTel plugin", e);
            throw e;
        }
        LOGGER.log(Level.INFO, "OTel plugin initialized");

        // jenkinsControllerOpenTelemetry.tracer.setDelegate(jenkinsControllerOpenTelemetry.openTelemetry.getTracer("jenkins"));
    }

    protected void checkChainOfSpans(Tree<SpanDataWrapper> spanTree, String... expectedSpanNames) {
        final List<String> expectedSpanNamesList = Arrays.asList(expectedSpanNames);
        final Iterator<String> expectedSpanNamesIt = expectedSpanNamesList.iterator();
        if (!expectedSpanNamesIt.hasNext()) {
            Assert.fail("No element in the list of expected spans for " + Arrays.asList(expectedSpanNames));
        }
        final String leafSpanName = expectedSpanNamesIt.next();
        Optional<Tree.Node<SpanDataWrapper>> actualNodeOptional = spanTree.breadthFirstSearchNodes(node -> Objects.equals(leafSpanName, node.getData().spanData.getName()));

        MatcherAssert.assertThat("Expected leaf span '" + leafSpanName + "' in chain of span" + expectedSpanNamesList + " not found", actualNodeOptional.isPresent(), CoreMatchers.is(true));

        while (expectedSpanNamesIt.hasNext()) {
            String expectedSpanName = expectedSpanNamesIt.next();
            actualNodeOptional = actualNodeOptional.get().getParent();
            final String actualSpanName = actualNodeOptional.get().getData().spanData.getName();
            MatcherAssert.assertThat("Expected span '" + expectedSpanName + "' in chain of span" + expectedSpanNamesList + " not found, actual is '" + actualSpanName + "'", actualSpanName, CoreMatchers.is(expectedSpanName));
        }
    }

    protected void dumpMetrics(Map<String, MetricData> exportedMetrics) {
        System.out.println("Metrics: " + exportedMetrics.size());
        System.out.println(exportedMetrics.values().stream().sorted(Comparator.comparing(MetricData::getName)).map(metric -> {
            MetricDataType metricType = metric.getType();
            String s = metric.getName() + "   " + metricType + " ";
            switch (metricType) {
                case LONG_SUM:
                    s += metric.getLongSumData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", "));
                    break;
                case DOUBLE_SUM:
                    s += metric.getDoubleSumData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", "));
                    break;
                case DOUBLE_GAUGE:
                    s += metric.getDoubleGaugeData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", "));
                    break;
                case LONG_GAUGE:
                    s += metric.getLongGaugeData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", "));
                    break;
                case SUMMARY:
                    break;
                default:

            }
            return s;
        }).collect(Collectors.joining(" \n")));
    }

    protected Tree<SpanDataWrapper> getGeneratedSpans() {
        return getGeneratedSpans(0);
    }

    protected Tree<SpanDataWrapper> getGeneratedSpans(int index) {
        CompletableResultCode completableResultCode = jenkinsControllerOpenTelemetry.getOpenTelemetrySdk().getSdkTracerProvider().forceFlush();
        completableResultCode.join(1, TimeUnit.SECONDS);
        List<SpanData> spans = InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems();

        final BiPredicate<Tree.Node<SpanDataWrapper>, Tree.Node<SpanDataWrapper>> parentChildMatcher = (spanDataNode1, spanDataNode2) -> {
            final SpanData spanData1 = spanDataNode1.getData().spanData;
            final SpanData spanData2 = spanDataNode2.getData().spanData;
            return Objects.equals(spanData1.getSpanId(), spanData2.getParentSpanId());
        };
        final List<Tree<SpanDataWrapper>> trees = Tree.of(spans.stream().map(span -> new SpanDataWrapper(span)).collect(Collectors.toList()), parentChildMatcher);
        System.out.println("## TREE VIEW OF SPANS ## ");
        for (Tree<SpanDataWrapper> tree : trees) {
            System.out.println(tree);
        }

        if (index < 0 || index >= trees.size()) {
            throw new IllegalArgumentException("No span found for index=" + index + ", trees.size()=" + trees.size());
        }
        return trees.get(index);
    }

    protected void assertEnvironmentVariables(EnvVars environment) {
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.SPAN_ID), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.TRACE_ID), CoreMatchers.is(CoreMatchers.notNullValue()));
        // See src/test/resources/io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.OTEL_TRACES_EXPORTER), CoreMatchers.is("otlp"));
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_ENDPOINT), CoreMatchers.is("http://otel-collector-contrib:4317"));
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_INSECURE), CoreMatchers.is("true"));
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_TIMEOUT), CoreMatchers.is("3000"));
    }

    protected void assertJobMetadata(AbstractBuild build, Tree<SpanDataWrapper> spans, String jobType) throws Exception {
        List<SpanDataWrapper> root = spans.byDepth().get(0);
        Attributes attributes = root.get(0).spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE), CoreMatchers.is(jobType));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_MULTIBRANCH_TYPE), CoreMatchers.nullValue());
    }

    protected void assertFreestyleJobMetadata(AbstractBuild build, Tree<SpanDataWrapper> spans) throws Exception {
        assertJobMetadata(build, spans, OtelUtils.FREESTYLE);
    }

    protected void assertMatrixJobMetadata(AbstractBuild build, Tree<SpanDataWrapper> spans) throws Exception {
        assertJobMetadata(build, spans, OtelUtils.MATRIX);
    }

    protected void assertMavenJobMetadata(AbstractBuild build, Tree<SpanDataWrapper> spans) throws Exception {
        assertJobMetadata(build, spans, OtelUtils.MAVEN);
    }

    protected void assertNodeMetadata(Tree<SpanDataWrapper> spans, String jobName, boolean withNode) throws Exception {
        Optional<Tree.Node<SpanDataWrapper>> shell = spans.breadthFirstSearchNodes(node -> jobName.equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(shell, CoreMatchers.is(CoreMatchers.notNullValue()));
        Attributes attributes = shell.get().getData().spanData.getAttributes();

        if (withNode) {
            MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL), CoreMatchers.not(Matchers.emptyOrNullString()));
        } else {
            MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_AGENT_LABEL), CoreMatchers.is(Matchers.emptyOrNullString()));
        }
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_AGENT_NAME), CoreMatchers.not(Matchers.emptyOrNullString()));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_AGENT_ID), Matchers.notNullValue());
    }

    protected void assertBuildStepMetadata(Tree<SpanDataWrapper> spans, String stepName, String pluginName) throws Exception {
        Optional<Tree.Node<SpanDataWrapper>> step = spans.breadthFirstSearchNodes(node -> stepName.equals(node.getData().spanData.getName()));
        MatcherAssert.assertThat(step, CoreMatchers.is(CoreMatchers.notNullValue()));
        Attributes attributes = step.get().getData().spanData.getAttributes();

        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_NAME), CoreMatchers.is(pluginName));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_PLUGIN_VERSION), CoreMatchers.is(CoreMatchers.notNullValue()));
    }

    /**
     * This method is used for testing that the correct spans and their children have been generated.
     * It returns a map,
     *      - key:    span name
     *      - value:  list of children span names
     */
    protected Map<String, List<String>> getSpanMapWithChildrenFromTree(Tree<SpanDataWrapper> spansTree) {
        Map<String, List<String>> spansTreeMap = new HashMap<>();
        // Stream all the nodes.
        spansTree.breadthFirstStreamNodes().forEach(node -> {
            String parentSpanName = node.getData().spanData.getName();
            List<String> childrenSpanNames = new ArrayList<>();

            // Get the children and for each one, store the name.
            node.getChildren().forEach(child -> childrenSpanNames.add(child.getData().spanData.getName()));

            // Put the span and its children on the map.
            spansTreeMap.put(parentSpanName, childrenSpanNames);
        });
        return spansTreeMap;
    }

    /**
     * This method is used for testing a span's data like attributes.
     * It returns a map,
     *      - key:    span name
     *      - value:  SpanData
     */
    protected Map<String, SpanData> getSpanDataMapFromTree(Tree<SpanDataWrapper> spansTree) {
        Map<String, SpanData> spansTreeMap = new HashMap<>();
        // Stream all the nodes.
        spansTree.breadthFirstStreamNodes().forEach(node ->
            spansTreeMap.put(node.getData().spanData.getName(), node.getData().spanData));
        return spansTreeMap;
    }

    // https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java
    @NonNull
    public static WorkflowJob scheduleAndFindBranchProject(@NonNull WorkflowMultiBranchProject mp, @NonNull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    // https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java
    public static @NonNull
    WorkflowJob findBranchProject(@NonNull WorkflowMultiBranchProject mp, @NonNull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    // https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java
    static void showIndexing(@NonNull WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }


    @AfterClass
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    public static class SpanDataWrapper {
        public final SpanData spanData;

        public SpanDataWrapper(SpanData spanData) {
            this.spanData = spanData;
        }

        @Override
        public String toString() {
            String result = spanData.getName();

            final Attributes attributes = spanData.getAttributes();
            if (attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE) != null) {
                result += ", function: " + attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_TYPE);
            }
            if (attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME) != null) {
                result += ", name: " + attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_NAME);
            }
            if (attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID) != null) {
                result += ", node.id: " + attributes.get(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID);
            }
            return result;
        }
    }
}
