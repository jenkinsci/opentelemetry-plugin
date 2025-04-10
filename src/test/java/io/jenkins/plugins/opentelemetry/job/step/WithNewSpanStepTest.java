/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.commons.lang3.SystemUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assume.assumeFalse;

public class WithNewSpanStepTest extends BaseIntegrationTest {

    private static WorkflowJob pipeline;

    @BeforeClass
    public static void setUp() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        jenkinsRule.createOnlineSlave();

        final String jobName = "test-pipeline-spans" + jobNameSuffix.incrementAndGet();
        pipeline = jenkinsRule.createProject(WorkflowJob.class, jobName);
    }

    @Test
    public void testLibCallWithUserDefinedSpan() throws Exception {
        String pipelineScript =
            "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "\n" +
            "def runBuilds() {\n" +
            "   withNewSpan(label: 'run-builds') {\n" +
            "       xsh (label: 'build-mod1', script: 'echo building-module-1') \n" +
            "       xsh (label: 'build-mod2', script: 'echo building-module-2') \n" +
            "   }\n" +
            "}\n" +
            "\n" +
            "node {\n" +
            "   stage('build') {\n" +
            "       runBuilds()" +
            "   }\n" +
            "}";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);

        // Check that the new spans exist.
        Assert.assertNotNull(spansTreeMap.get("run-builds"));
        Assert.assertNotNull(spansTreeMap.get("build-mod1"));
        Assert.assertNotNull(spansTreeMap.get("build-mod2"));

        // Check span children.
        Assert.assertTrue(spansTreeMap.get("run-builds").containsAll(Arrays.asList("build-mod1", "build-mod2")));
        Assert.assertEquals(2, spansTreeMap.get("run-builds").size());
    }

    @Test
    public void testUserDefinedSpanWithAttributes() throws Exception {
        String pipelineScript =
            "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "node {\n" +
            "   stage('build') {\n" +
            "       withNewSpan(label: 'run-builds', attributes: ([\n" +
            "           spanAttribute(key: 'modules-num', value: '2'),\n" +
            "           spanAttribute(key: 'command', value: 'build')\n" +
            "       ])) {\n" +
            "           xsh (label: 'build-mod1', script: 'echo building-module-1') \n" +
            "           echo 'building-module-2'\n" +
            "       }\n" +
            "   }\n" +
            "}";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);
        Map<String, SpanData> spansDataMap = getSpanDataMapFromTree(spansTree);

        // Check that the new spans exist.
        Assert.assertNotNull(spansTreeMap.get("run-builds"));
        Assert.assertNotNull(spansTreeMap.get("build-mod1"));

        // Check span children.
        Assert.assertTrue(spansTreeMap.get("run-builds").contains("build-mod1"));
        Assert.assertEquals(1, spansTreeMap.get("run-builds").size());

        // Check user-defined span attributes.
        Attributes parentAttributes = spansDataMap.get("run-builds").getAttributes();
        Assert.assertEquals("2", parentAttributes.get(AttributeKey.stringKey("modules-num")));
        Assert.assertEquals("build", parentAttributes.get(AttributeKey.stringKey("command")));

        // Span attributes are also passed to children.
        Attributes childAttributes = spansDataMap.get("build-mod1").getAttributes();
        Assert.assertEquals("2", childAttributes.get(AttributeKey.stringKey("modules-num")));
        Assert.assertEquals("build", childAttributes.get(AttributeKey.stringKey("command")));
    }

    @Test
    public void testUserDefinedSpanWithChildren() throws Exception {
        String pipelineScript =
            "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "node {\n" +
            "   stage('build') {\n" +
            "       withNewSpan(label: 'run-builds') {\n" +
            "           xsh (label: 'build-mod1', script: 'echo building-module-1') \n" +
            "           xsh (label: 'build-mod2', script: 'echo building-module-2') \n" +
            "       }\n" +
            "   }\n" +
            "}";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);

        // Use a non-existent span name.
        Assert.assertNull(spansTreeMap.get("non-existent-span"));

        // Check that the new spans exist.
        Assert.assertNotNull(spansTreeMap.get("run-builds"));
        Assert.assertNotNull(spansTreeMap.get("build-mod1"));
        Assert.assertNotNull(spansTreeMap.get("build-mod2"));

        // Check span children.
        Assert.assertTrue(spansTreeMap.get("run-builds").containsAll(Arrays.asList("build-mod1", "build-mod2")));
        Assert.assertFalse(spansTreeMap.get("run-builds").containsAll(Arrays.asList("build-mod1", "non-existent")));
        Assert.assertEquals(2, spansTreeMap.get("run-builds").size());
    }

    @Test
    public void testUserDefinedSpanWithNoChildren() throws Exception {
        String pipelineScript =
            "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "node {\n" +
            "   stage('build') {\n" +
            "       withNewSpan(label: 'run-builds') {\n" +
            "           echo 'building-module-1'\n" +
            "           echo 'building-module-2'\n" +
            "       }\n" +
            "   }\n" +
            "}";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);

        // Check that the new spans exist.
        Assert.assertNotNull(spansTreeMap.get("run-builds"));

        // Check span children.
        Assert.assertTrue(spansTreeMap.get("run-builds").isEmpty());
        Assert.assertEquals(0, spansTreeMap.get("run-builds").size());
    }

    @Test
    public void testUserDefinedSpanWithAttributesNotPassedOnToChildren() throws Exception {
        String pipelineScript =
            "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
            "node {\n" +
            "   stage('build') {\n" +
            "       withNewSpan(label: 'run-builds', attributes: ([\n" +
            "           spanAttribute(key: 'modules-num', value: '2'),\n" +
            "           spanAttribute(key: 'command', value: 'build')\n" +
            "       ]), setAttributesOnlyOnParent: true) {\n" +
            "           xsh (label: 'build-mod1', script: 'echo building-module-1') \n" +
            "           echo 'building-module-2'\n" +
            "       }\n" +
            "   }\n" +
            "}";

        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spansTree = getBuildTrace();
        Map<String, List<String>> spansTreeMap = getSpanMapWithChildrenFromTree(spansTree);
        Map<String, SpanData> spansDataMap = getSpanDataMapFromTree(spansTree);

        // Check that the new spans exist.
        Assert.assertNotNull(spansTreeMap.get("run-builds"));

        // Check span children.
        Assert.assertTrue(spansTreeMap.get("run-builds").contains("build-mod1"));
        Assert.assertEquals(1, spansTreeMap.get("run-builds").size());

        // Check user-defined span attributes.
        Attributes parentAttributes = spansDataMap.get("run-builds").getAttributes();
        Assert.assertEquals("2", parentAttributes.get(AttributeKey.stringKey("modules-num")));
        Assert.assertEquals("build", parentAttributes.get(AttributeKey.stringKey("command")));

        // Span attributes are NOT passed to children.
        Attributes childAttributes = spansDataMap.get("build-mod1").getAttributes();
        Assert.assertNull(childAttributes.get(AttributeKey.stringKey("modules-num")));
        Assert.assertNull(childAttributes.get(AttributeKey.stringKey("command")));
    }

}
