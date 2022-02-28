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
            "my-war/master", 3l, "/job/:jobFullName/:runNumber");
        String pathInfo = "/job/my-war/job/master/3";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobUrlBuildConsole() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", 3l, "/job/:jobFullName/:runNumber/console");
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
    public void testParseBlueOceanRestPipelineUrl_testSummary() {
        //
        // /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/
        // /blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/19/log/

        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "ecommerce-antifraud/main", 110l, "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/*");
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/blueTestSummary/";
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseBlueOceanRestPipelineUrl_flowNodeSteps() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "ecommerce-antifraud/main", 110l, "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/*");
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/";
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseBlueOceanRestPipelineUrl_flowNodeStepLogs() {
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "ecommerce-antifraud/main", 110l, "/blue/rest/organizations/:organization/pipelines/:pipelineName/branches/:branch/runs/:runNumber/*");
        String pathInfo = "/blue/rest/organizations/jenkins/pipelines/ecommerce-antifraud/branches/main/runs/110/nodes/13/steps/19/log/";
        verifyBlueOceanRestPipelineUrlParsing(expected, pathInfo);
    }

    private void verifyBlueOceanRestPipelineUrlParsing(OpenTelemetryServletFilter.ParsedJobUrl expected, String pathInfo) {
        List<String> pathInfoTokens = Collections.list(new StringTokenizer(pathInfo, "/")).stream()
            .map(token -> (String) token)
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toList());

        OpenTelemetryServletFilter.ParsedJobUrl actual = new OpenTelemetryServletFilter(OpenTelemetry.noop().getTracer("test")).parseBlueOceanRestPipelineUrl(pathInfoTokens);
        System.out.println(actual);
        Assert.assertEquals(expected, actual);
    }
}
