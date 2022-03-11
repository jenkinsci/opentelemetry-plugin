/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.servlet;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

public class OpenTelemetryServletFilterTest {

    @Test
    public void testParseJobUrlLatestBuildConsole() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", null, "/job/:jobFullName/lastBuild/console");
        String pathInfo = "/job/my-war/job/master/lastBuild/console";
        verifyJobUrlParsing(expected, pathInfo);
    }

    /**
     * TODO trim trailing slash
     */
    @Test
    public void testParseJobUrlLastBuild() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", null, "/job/:jobFullName/lastBuild");
        String pathInfo = "/job/my-war/job/master/lastBuild/";
        verifyJobUrlParsing(expected, pathInfo);
    }


    @Test
    public void testParseJobUrlLastBuildNoTrailingSlash() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", null, "/job/:jobFullName/lastBuild");
        String pathInfo = "/job/my-war/job/master/lastBuild";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobUrlBuildNoTrailingSlash() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", 3L, "/job/:jobFullName/:runNumber");
        String pathInfo = "/job/my-war/job/master/3";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobUrlBuildConsole() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", 3L, "/job/:jobFullName/:runNumber/console");
        String pathInfo = "/job/my-war/job/master/3/console";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobUrl() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", null, "/job/:jobFullName");
        String pathInfo = "/job/my-war/job/master/";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void job_run_execution_node() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "ecommerce-antifraud/main", 110L, "/job/:jobFullName/:runNumber/execution/:execution/*");
        String pathInfo = "/job/ecommerce-antifraud/job/main/110/execution/node/13/wfapi/describe";
        verifyJobUrlParsing(expected, pathInfo);
    }

    private void verifyJobUrlParsing(OpenTelemetryServletFilter.ParsedJobUrl expected, String pathInfo) {
        List<String> pathInfoTokens = Collections.list(new StringTokenizer(pathInfo, "/")).stream()
            .map(token -> (String) token)
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toList());

        OpenTelemetryServletFilter.ParsedJobUrl actual = new OpenTelemetryServletFilter(OpenTelemetry.noop().getTracer("test")).parseJobUrl(pathInfoTokens);
        System.out.println(actual);
        Assert.assertEquals(expected, actual);
    }


    @Test
    public void url_bo_multibranch_pipeline_run_home() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "ecommerce-antifraud/main", 110L, null, null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_multibranch_pipeline_run_testSummary() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/blueTestSummary/";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "ecommerce-antifraud/main", 110L, null, null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/blueTestSummary");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_multibranch_pipeline_run_node_steps() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "ecommerce-antifraud/main", 110L, "13", null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/nodes/:node/steps");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_pipeline_run() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "my-war", 1L, null, null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_pipeline_run_blueTestSummary() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/blueTestSummary/";
        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "my-war", 1L, null, null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/blueTestSummary");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_pipeline_run_changeSet() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/changeSet/";
        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "my-war", 1L, null, null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/changeSet");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_pipeline_activity() {
        String pathInfo = "/blue/organizations/jenkins/my-war-pipeline/activity";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "my-war", 1L, null, null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/activity");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_pipeline_run_steps() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/steps/";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "my-war", 1L, null, null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/steps");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_pipeline_run_nodes() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/nodes/";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "my-war", 1L, null, null, "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/nodes");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }


    @Test
    public void url_bo_pipeline_run_node_steps_log() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/steps/5/log/";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "my-war", 1L, null, 5, "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/steps/:step/log");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void url_bo_multibranch_pipeline_run_node_step_unexpected() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/19/unexpected/path";

        OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected = new OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl(
            "ecommerce-antifraud/main", 110L, "13", 19, "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/nodes/:node/steps/:step/*");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobBuildArtifactUrl() {
        // /job/:jobFullName/:runNumber/artifact/target/anti-fraud-0.0.1-SNAPSHOT.jar
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "ecommerce-antifraud/main", 110L, "/job/:jobFullName/:runNumber/artifact/:artifact");
        String pathInfo = "/job/ecommerce-antifraud/job/main/110/artifact/target/anti-fraud-0.0.1-SNAPSHOT.jar";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobLastBuildArtifactUrl() {
        // /job/:jobFullName/:runNumber/artifact/target/anti-fraud-0.0.1-SNAPSHOT.jar
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "ecommerce-antifraud/main", null, "/job/:jobFullName/lastBuild/artifact/:artifact");
        String pathInfo = "/job/ecommerce-antifraud/job/main/lastBuild/artifact/target/anti-fraud-0.0.1-SNAPSHOT.jar";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobBuildDescriptorMethodUrl() {
        // /job/ecommerce-antifraud/job/main/128/descriptorByName/com.cloudbees.jenkins.support.impl.RunDirectoryComponent/checkMaxDepth
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "ecommerce-antifraud/main", 110L, "/job/:jobFullName/:runNumber/descriptorByName/:descriptor/:method");
        String pathInfo = "/job/ecommerce-antifraud/job/main/110/descriptorByName/com.cloudbees.jenkins.support.impl.RunDirectoryComponent/checkMaxDepth";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobLastBuildDescriptorMethodUrl() {
        // /job/ecommerce-antifraud/job/main/128/descriptorByName/com.cloudbees.jenkins.support.impl.RunDirectoryComponent/checkMaxDepth
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "ecommerce-antifraud/main", null, "/job/:jobFullName/lastBuild/descriptorByName/:descriptor/:method");
        String pathInfo = "/job/ecommerce-antifraud/job/main/lastBuild/descriptorByName/com.cloudbees.jenkins.support.impl.RunDirectoryComponent/checkMaxDepth";
        verifyJobUrlParsing(expected, pathInfo);
    }


    private void verifyBlueOceanRestPipelineUrlParsing(OpenTelemetryServletFilter.ParsedBlueOceanPipelineJobUrl expected, String pathInfo) {
        List<String> pathInfoTokens = Collections.list(new StringTokenizer(pathInfo, "/")).stream()
            .map(token -> (String) token)
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toList());

        try {
            OpenTelemetryServletFilter.ParsedJobUrl actual = new OpenTelemetryServletFilter(OpenTelemetry.noop().getTracer("test")).parseBlueOceanRestPipelineUrl(pathInfoTokens);

            System.out.println(actual);
            Assert.assertEquals(expected, actual);
        } catch (IndexOutOfBoundsException e) {
            throw new AssertionError("Exception parsing " + pathInfo + " - " + e.getMessage(), e);
        }
    }
}
