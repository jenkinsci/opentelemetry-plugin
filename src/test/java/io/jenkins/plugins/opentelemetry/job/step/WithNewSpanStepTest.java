/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WithNewSpanStepTest extends BaseIntegrationTest {

    private static WorkflowJob pipeline;

    @BeforeAll
    static void setUp() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        jenkinsRule.createOnlineSlave();

        final String jobName = "test-pipeline-spans" + jobNameSuffix.incrementAndGet();
        pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
    }

    @Test
    void testLibCallWithUserDefinedSpan() throws Exception {
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};

            def runBuilds() {
               withNewSpan(label: 'run-builds') {
                   xsh (label: 'build-mod1', script: 'echo building-module-1')\s
                   xsh (label: 'build-mod2', script: 'echo building-module-2')\s
               }
            }

            node {
               stage('build') {
                   runBuilds()\
               }
            }""";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);

        // Check that the new spans exist.
        assertNotNull(spansTreeMap.get("run-builds"));
        assertNotNull(spansTreeMap.get("build-mod1"));
        assertNotNull(spansTreeMap.get("build-mod2"));

        // Check span children.
        assertTrue(spansTreeMap.get("run-builds").containsAll(Arrays.asList("build-mod1", "build-mod2")));
        assertEquals(2, spansTreeMap.get("run-builds").size());
    }

    @Test
    void testUserDefinedSpanWithAttributes() throws Exception {
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node {
               stage('build') {
                   withNewSpan(label: 'run-builds', attributes: ([
                       spanAttribute(key: 'modules-num', value: '2'),
                       spanAttribute(key: 'command', value: 'build')
                   ])) {
                       xsh (label: 'build-mod1', script: 'echo building-module-1')\s
                       echo 'building-module-2'
                   }
               }
            }""";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);
        Map<String, SpanData> spansDataMap = getSpanDataMapFromTree(spansTree);

        // Check that the new spans exist.
        assertNotNull(spansTreeMap.get("run-builds"));
        assertNotNull(spansTreeMap.get("build-mod1"));

        // Check span children.
        assertTrue(spansTreeMap.get("run-builds").contains("build-mod1"));
        assertEquals(1, spansTreeMap.get("run-builds").size());

        // Check user-defined span attributes.
        Attributes parentAttributes = spansDataMap.get("run-builds").getAttributes();
        assertEquals("2", parentAttributes.get(AttributeKey.stringKey("modules-num")));
        assertEquals("build", parentAttributes.get(AttributeKey.stringKey("command")));

        // Span attributes are also passed to children.
        Attributes childAttributes = spansDataMap.get("build-mod1").getAttributes();
        assertEquals("2", childAttributes.get(AttributeKey.stringKey("modules-num")));
        assertEquals("build", childAttributes.get(AttributeKey.stringKey("command")));
    }

    @Test
    void testUserDefinedSpanWithChildren() throws Exception {
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node {
               stage('build') {
                   withNewSpan(label: 'run-builds') {
                       xsh (label: 'build-mod1', script: 'echo building-module-1')\s
                       xsh (label: 'build-mod2', script: 'echo building-module-2')\s
                   }
               }
            }""";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);

        // Use a non-existent span name.
        assertNull(spansTreeMap.get("non-existent-span"));

        // Check that the new spans exist.
        assertNotNull(spansTreeMap.get("run-builds"));
        assertNotNull(spansTreeMap.get("build-mod1"));
        assertNotNull(spansTreeMap.get("build-mod2"));

        // Check span children.
        assertTrue(spansTreeMap.get("run-builds").containsAll(Arrays.asList("build-mod1", "build-mod2")));
        assertFalse(spansTreeMap.get("run-builds").containsAll(Arrays.asList("build-mod1", "non-existent")));
        assertEquals(2, spansTreeMap.get("run-builds").size());
    }

    @Test
    void testUserDefinedSpanWithNoChildren() throws Exception {
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node {
               stage('build') {
                   withNewSpan(label: 'run-builds') {
                       echo 'building-module-1'
                       echo 'building-module-2'
                   }
               }
            }""";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);

        // Check that the new spans exist.
        assertNotNull(spansTreeMap.get("run-builds"));

        // Check span children.
        assertTrue(spansTreeMap.get("run-builds").isEmpty());
        assertEquals(0, spansTreeMap.get("run-builds").size());
    }

    @Test
    void testUserDefinedSpanWithAttributesNotPassedOnToChildren() throws Exception {
        String pipelineScript = """
            def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};
            node {
               stage('build') {
                   withNewSpan(label: 'run-builds', attributes: ([
                       spanAttribute(key: 'modules-num', value: '2'),
                       spanAttribute(key: 'command', value: 'build')
                   ]), setAttributesOnlyOnParent: true) {
                       xsh (label: 'build-mod1', script: 'echo building-module-1')\s
                       echo 'building-module-2'
                   }
               }
            }""";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);
        Map<String, SpanData> spansDataMap = getSpanDataMapFromTree(spansTree);

        // Check that the new spans exist.
        assertNotNull(spansTreeMap.get("run-builds"));

        // Check span children.
        assertTrue(spansTreeMap.get("run-builds").contains("build-mod1"));
        assertEquals(1, spansTreeMap.get("run-builds").size());

        // Check user-defined span attributes.
        Attributes parentAttributes = spansDataMap.get("run-builds").getAttributes();
        assertEquals("2", parentAttributes.get(AttributeKey.stringKey("modules-num")));
        assertEquals("build", parentAttributes.get(AttributeKey.stringKey("command")));

        // Span attributes are NOT passed to children.
        Attributes childAttributes = spansDataMap.get("build-mod1").getAttributes();
        assertNull(childAttributes.get(AttributeKey.stringKey("modules-num")));
        assertNull(childAttributes.get(AttributeKey.stringKey("command")));
    }
}
