/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import com.github.rutledgepaulv.prune.Tree;
import hudson.EnvVars;
import hudson.ExtensionList;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.OTelEnvironmentVariablesConventions;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import jenkins.plugins.git.ExtendedGitSampleRepoRule;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
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

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static com.google.common.base.Verify.verify;
import static org.junit.Assert.fail;

public class BaseIntegrationTest {
    static {
        OpenTelemetrySdkProvider.TESTING_INMEMORY_MODE = true;
        OpenTelemetrySdkProvider.TESTING_SPAN_EXPORTER = InMemorySpanExporter.create();
        OpenTelemetrySdkProvider.TESTING_METRICS_EXPORTER = InMemoryMetricExporter.create();
    }

    final static AtomicInteger jobNameSuffix = new AtomicInteger();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    @ConfiguredWithCode("jcasc-elastic-backend.yml")
    public static JenkinsConfiguredWithCodeRule jenkinsRule = new JenkinsConfiguredWithCodeRule();

    @Rule
    public ExtendedGitSampleRepoRule sampleRepo = new ExtendedGitSampleRepoRule();

    static OpenTelemetrySdkProvider openTelemetrySdkProvider;

    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
        jenkinsRule.waitUntilNoActivity();
        ((InMemorySpanExporter) OpenTelemetrySdkProvider.TESTING_SPAN_EXPORTER).reset();
        ((InMemoryMetricExporter) OpenTelemetrySdkProvider.TESTING_METRICS_EXPORTER).reset();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        System.out.println("beforeClass()");
        System.out.println("Wait for jenkins to start...");
        jenkinsRule.waitUntilNoActivity();
        System.out.println("Jenkins started");

        ExtensionList<OpenTelemetrySdkProvider> openTelemetrySdkProviders = jenkinsRule.getInstance().getExtensionList(OpenTelemetrySdkProvider.class);
        verify(openTelemetrySdkProviders.size() == 1, "Number of openTelemetrySdkProviders: %s", openTelemetrySdkProviders.size());
        openTelemetrySdkProvider = openTelemetrySdkProviders.get(0);

        // verify(openTelemetrySdkProvider.openTelemetry == null, "OpenTelemetrySdkProvider has already been configured");
        openTelemetrySdkProvider.initializeForTesting();

        // openTelemetrySdkProvider.tracer.setDelegate(openTelemetrySdkProvider.openTelemetry.getTracer("jenkins"));
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
                    s += metric.getLongSumData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", ")) + "";
                    break;
                case DOUBLE_SUM:
                    s += metric.getDoubleSumData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", ")) + "";
                    break;
                case DOUBLE_GAUGE:
                    s += metric.getDoubleGaugeData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", ")) + "";
                    break;
                case LONG_GAUGE:
                    s += metric.getLongGaugeData().getPoints().stream().map(point -> String.valueOf(point.getValue())).collect(Collectors.joining(", ")) + "";
                    break;
                case SUMMARY:
                    break;
                default:

            }
            return s;
        }).collect(Collectors.joining(" \n")));
    }

    protected Tree<SpanDataWrapper> getGeneratedSpans() {

        CompletableResultCode completableResultCode = this.openTelemetrySdkProvider.getOpenTelemetrySdk().getSdkTracerProvider().forceFlush();
        completableResultCode.join(1, TimeUnit.SECONDS);
        List<SpanData> spans = ((InMemorySpanExporter) OpenTelemetrySdkProvider.TESTING_SPAN_EXPORTER).getFinishedSpanItems();

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

        return trees.get(0);
    }

    protected void assertEnvironmentVariables(EnvVars environment) {
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.SPAN_ID), CoreMatchers.is(CoreMatchers.notNullValue()));
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.TRACE_ID), CoreMatchers.is(CoreMatchers.notNullValue()));
        // See src/test/resources/io/jenkins/plugins/opentelemetry/jcasc-elastic-backend.yml
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_ENDPOINT), CoreMatchers.is("http://otel-collector-contrib:4317"));
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_INSECURE), CoreMatchers.is("true"));
        MatcherAssert.assertThat(environment.get(OTelEnvironmentVariablesConventions.OTEL_EXPORTER_OTLP_TIMEOUT), CoreMatchers.is("3000"));
    }

    // https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java
    @Nonnull
    public static WorkflowJob scheduleAndFindBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    // https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java
    public static @Nonnull WorkflowJob findBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    // https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java
    static void showIndexing(@Nonnull WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }


    @AfterClass
    public static void afterClass() {
        GlobalOpenTelemetry.resetForTest();
    }

    static class SpanDataWrapper {
        final SpanData spanData;

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
