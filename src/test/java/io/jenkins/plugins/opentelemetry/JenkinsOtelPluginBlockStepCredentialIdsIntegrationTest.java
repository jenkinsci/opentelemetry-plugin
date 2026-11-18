/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Result;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporterProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;

/**
 * With the setting for credential IDs off, the ID of a missing credential is in no span of a block step, nor in any
 * span that a block step handler adds, whatever block step encloses the {@code withCredentials} that fails.
 */
public class JenkinsOtelPluginBlockStepCredentialIdsIntegrationTest extends BaseIntegrationTest {

    private static final String MISSING_ID = "planted-missing-id-9a41";

    /** Every block step around a withCredentials whose credential is missing. */
    private static final String NESTED_PIPELINE = """
            node() {
                stage('ze-stage') {
                    timeout(time: 5, unit: 'MINUTES') {
                        withEnv(['ZE_VAR=1']) {
                            withCredentials([string(credentialsId: '%s', variable: 'T')]) {
                                echo 'never'
                            }
                        }
                    }
                }
            }
            """.formatted(MISSING_ID);

    private static final String ALL_BLOCK_STEPS = "timeout,withEnv,withCredentials";

    /** The span names of the block steps and of the spans that their handlers add. */
    private static final Set<String> BLOCK_STEP_SPAN_NAMES = Set.of("timeout", "withEnv", "withCredentials");

    @Test
    public void testIdIsInNoBlockStepSpanWhenIdsAreOff() throws Exception {
        configure(ALL_BLOCK_STEPS, false);

        runPipeline(NESTED_PIPELINE, Result.FAILURE);

        List<SpanData> spans = blockStepSpans();
        assertEquals(
                Set.of("timeout", "withEnv", "withCredentials"),
                spans.stream().map(SpanData::getName).collect(Collectors.toSet()));
        for (SpanData span : spans) {
            assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode(), span.getName());
            assertTrue(
                    span.getEvents().stream().anyMatch(e -> "exception".equals(e.getName())),
                    "exception event on " + span.getName());
            assertNoMissingId(span);
        }
    }

    @Test
    public void testIdIsInNoTimeoutSpanWhenOnlyTimeoutIsTracedAndIdsAreOff() throws Exception {
        configure("timeout", false);

        runPipeline("""
                node() {
                    stage('ze-stage') {
                        timeout(time: 5, unit: 'MINUTES') {
                            withCredentials([string(credentialsId: '%s', variable: 'T')]) {
                                echo 'never'
                            }
                        }
                    }
                }
                """.formatted(MISSING_ID), Result.FAILURE);

        List<SpanData> spans = blockStepSpans();
        assertEquals(Set.of("timeout"), spans.stream().map(SpanData::getName).collect(Collectors.toSet()));
        spans.forEach(this::assertNoMissingId);
        assertEquals(
                "Could not find credentials entry with ID '<redacted>'",
                spans.get(0).getStatus().getDescription());
    }

    @Test
    public void testIdIsInTheBlockStepSpansWhenIdsAreOn() throws Exception {
        configure(ALL_BLOCK_STEPS, true);

        runPipeline(NESTED_PIPELINE, Result.FAILURE);

        for (SpanData span : blockStepSpans()) {
            assertTrue(span.getStatus().getDescription().contains(MISSING_ID), "status of " + span.getName());
            assertTrue(
                    texts(span).stream()
                            .anyMatch(text -> text.startsWith("exception.message=") && text.contains(MISSING_ID)),
                    "exception event of " + span.getName());
        }
    }

    @Test
    public void testOtherQuotedTextOfAnErrorIsKeptWhenIdsAreOff() throws Exception {
        configure("timeout", false);

        runPipeline("""
                node() {
                    stage('ze-stage') {
                        timeout(time: 5, unit: 'MINUTES') {
                            error "Cannot invoke 'ze-method' on 'ze-object'"
                        }
                    }
                }
                """, Result.FAILURE);

        for (SpanData span : blockStepSpans()) {
            assertEquals(
                    "Cannot invoke 'ze-method' on 'ze-object'",
                    span.getStatus().getDescription(),
                    "status of " + span.getName());
        }
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    private void configure(String blockSteps, boolean includeIds) {
        JenkinsOpenTelemetryPluginConfiguration configuration = JenkinsOpenTelemetryPluginConfiguration.get();
        configuration.setBlockSteps(blockSteps);
        configuration.setIncludeCredentialIds(includeIds);
    }

    private void runPipeline(String script, Result expected) throws Exception {
        jenkinsRule.createOnlineSlave();
        WorkflowJob job = jenkinsRule.createProject(
                WorkflowJob.class, "test-block-step-credential-ids-" + jobNameSuffix.incrementAndGet());
        job.setDefinition(new CpsFlowDefinition(script, true));
        jenkinsRule.assertBuildStatus(expected, job.scheduleBuild2(0));
        getBuildTrace();
    }

    private List<SpanData> blockStepSpans() {
        return InMemorySpanExporterProvider.LAST_CREATED_INSTANCE.getFinishedSpanItems().stream()
                .filter(span -> BLOCK_STEP_SPAN_NAMES.contains(span.getName()))
                .collect(Collectors.toList());
    }

    private void assertNoMissingId(SpanData span) {
        texts(span).forEach(text -> assertFalse(text.contains(MISSING_ID), "ID in span " + span.getName()));
    }

    /** The name, status, attributes and events of the span, each attribute as {@code key=value}. */
    private static List<String> texts(SpanData span) {
        List<String> texts = new ArrayList<>();
        texts.add(span.getName());
        texts.add(String.valueOf(span.getStatus().getDescription()));
        span.getAttributes().forEach((key, value) -> texts.add(key.getKey() + "=" + value));
        span.getEvents().forEach(event -> {
            texts.add(event.getName());
            event.getAttributes().forEach((key, value) -> texts.add(key.getKey() + "=" + value));
        });
        return texts;
    }
}
