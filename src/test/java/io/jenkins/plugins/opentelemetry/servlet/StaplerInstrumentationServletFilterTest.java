/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class StaplerInstrumentationServletFilterTest {

    @Test
    void testParseJobUrlLatestBuildConsole() {
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "my-war/master", null, "/job/:jobFullName/lastBuild/console");
        String pathInfo = "/job/my-war/job/master/lastBuild/console";
        verifyJobUrlParsing(expected, pathInfo);
    }

    /**
     * TODO trim trailing slash
     */
    @Test
    void testParseJobUrlLastBuild() {
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "my-war/master", null, "/job/:jobFullName/lastBuild");
        String pathInfo = "/job/my-war/job/master/lastBuild/";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    void testParseJobUrlLastBuildNoTrailingSlash() {
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "my-war/master", null, "/job/:jobFullName/lastBuild");
        String pathInfo = "/job/my-war/job/master/lastBuild";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    void testParseJobUrlBuildNoTrailingSlash() {
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "my-war/master", 3L, "/job/:jobFullName/:runNumber");
        String pathInfo = "/job/my-war/job/master/3";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    void testParseJobUrlBuildConsole() {
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "my-war/master", 3L, "/job/:jobFullName/:runNumber/console");
        String pathInfo = "/job/my-war/job/master/3/console";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    void testParseJobUrl() {
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl("my-war/master", null, "/job/:jobFullName");
        String pathInfo = "/job/my-war/job/master/";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    void job_run_execution_node() {
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "ecommerce-antifraud/main", 110L, "/job/:jobFullName/:runNumber/execution/:execution/*");
        String pathInfo = "/job/ecommerce-antifraud/job/main/110/execution/node/13/wfapi/describe";
        verifyJobUrlParsing(expected, pathInfo);
    }

    private void verifyJobUrlParsing(StaplerInstrumentationServletFilter.ParsedJobUrl expected, String pathInfo) {
        List<String> pathInfoTokens = Collections.list(new StringTokenizer(pathInfo, "/")).stream()
                .map(token -> (String) token)
                .filter(t -> !t.isEmpty())
                .toList();

        StaplerInstrumentationServletFilter.ParsedJobUrl actual =
                newStaplerInstrumentationServletFilter().parseJobUrl(pathInfoTokens);
        System.out.println(actual);
        assertEquals(expected, actual);
    }

    @Test
    void url_bo_multibranch_pipeline_run_home() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "ecommerce-antifraud/main",
                        110L,
                        null,
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_multibranch_pipeline_run_testSummary() {
        String pathInfo =
                "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/blueTestSummary/";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "ecommerce-antifraud/main",
                        110L,
                        null,
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/blueTestSummary");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_multibranch_pipeline_run_node_steps() {
        String pathInfo =
                "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "ecommerce-antifraud/main",
                        110L,
                        "13",
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/nodes/:node/steps");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_pipeline_run() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "my-war",
                        1L,
                        null,
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_pipeline_run_blueTestSummary() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/blueTestSummary/";
        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "my-war",
                        1L,
                        null,
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/blueTestSummary");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_pipeline_run_changeSet() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/changeSet/";
        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "my-war",
                        1L,
                        null,
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/changeSet");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_pipeline_activity() {
        String pathInfo = "/blue/organizations/jenkins/my-war/activity";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "my-war", null, null, null, "/blue/organizations/:organization/:pipelineName/activity");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_pipeline_run_steps() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/steps/";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "my-war",
                        1L,
                        null,
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/steps");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_pipeline_run_nodes() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/nodes/";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "my-war",
                        1L,
                        null,
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/nodes");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_rest_pipeline_scm() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/scm/content";
        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "ecommerce-antifraud",
                        null,
                        null,
                        null,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/*");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_pipeline_run_node_steps_log() {
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/my-war/runs/1/steps/5/log/";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "my-war",
                        1L,
                        null,
                        5,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/runs/:runNumber/steps/:step/log");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void url_bo_multibranch_pipeline_run_node_step_unexpected() {
        String pathInfo =
                "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/19/unexpected/path";

        StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl(
                        "ecommerce-antifraud/main",
                        110L,
                        "13",
                        19,
                        "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/nodes/:node/steps/:step/*");
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    void testParseJobBuildArtifactUrl() {
        // /job/:jobFullName/:runNumber/artifact/target/anti-fraud-0.0.1-SNAPSHOT.jar
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "ecommerce-antifraud/main", 110L, "/job/:jobFullName/:runNumber/artifact/:artifact");
        String pathInfo = "/job/ecommerce-antifraud/job/main/110/artifact/target/anti-fraud-0.0.1-SNAPSHOT.jar";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    void testParseJobLastBuildArtifactUrl() {
        // /job/:jobFullName/:runNumber/artifact/target/anti-fraud-0.0.1-SNAPSHOT.jar
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "ecommerce-antifraud/main", null, "/job/:jobFullName/lastBuild/artifact/:artifact");
        String pathInfo = "/job/ecommerce-antifraud/job/main/lastBuild/artifact/target/anti-fraud-0.0.1-SNAPSHOT.jar";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    void testParseJobBuildDescriptorMethodUrl() {
        // /job/ecommerce-antifraud/job/main/128/descriptorByName/com.cloudbees.jenkins.support.impl.RunDirectoryComponent/checkMaxDepth
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "ecommerce-antifraud/main",
                        110L,
                        "/job/:jobFullName/:runNumber/descriptorByName/:descriptor/:method");
        String pathInfo =
                "/job/ecommerce-antifraud/job/main/110/descriptorByName/com.cloudbees.jenkins.support.impl.RunDirectoryComponent/checkMaxDepth";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    void testParseJobLastBuildDescriptorMethodUrl() {
        // /job/ecommerce-antifraud/job/main/128/descriptorByName/com.cloudbees.jenkins.support.impl.RunDirectoryComponent/checkMaxDepth
        StaplerInstrumentationServletFilter.ParsedJobUrl expected =
                new StaplerInstrumentationServletFilter.ParsedJobUrl(
                        "ecommerce-antifraud/main",
                        null,
                        "/job/:jobFullName/lastBuild/descriptorByName/:descriptor/:method");
        String pathInfo =
                "/job/ecommerce-antifraud/job/main/lastBuild/descriptorByName/com.cloudbees.jenkins.support.impl.RunDirectoryComponent/checkMaxDepth";
        verifyJobUrlParsing(expected, pathInfo);
    }

    private void verifyBlueOceanRestPipelineUrlParsing(
            StaplerInstrumentationServletFilter.ParsedBlueOceanPipelineJobUrl expected, String pathInfo) {
        List<String> pathInfoTokens = Collections.list(new StringTokenizer(pathInfo, "/")).stream()
                .map(token -> (String) token)
                .filter(t -> !t.isEmpty())
                .toList();

        try {
            StaplerInstrumentationServletFilter staplerInstrumentationServletFilter =
                    newStaplerInstrumentationServletFilter();

            StaplerInstrumentationServletFilter.ParsedJobUrl actual =
                    staplerInstrumentationServletFilter.parseBlueOceanRestPipelineUrl(pathInfoTokens);

            System.out.println(actual);
            assertEquals(expected, actual);
        } catch (IndexOutOfBoundsException e) {
            throw new AssertionError("Exception parsing " + pathInfo + " - " + e.getMessage(), e);
        }
    }

    private static @Nonnull StaplerInstrumentationServletFilter newStaplerInstrumentationServletFilter() {
        StaplerInstrumentationServletFilter staplerInstrumentationServletFilter =
                new StaplerInstrumentationServletFilter();
        staplerInstrumentationServletFilter.tracer = OpenTelemetry.noop().getTracer("test");
        staplerInstrumentationServletFilter.capturedRequestParameters = Collections.emptyList();
        return staplerInstrumentationServletFilter;
    }
}
