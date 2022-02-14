/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.servlet;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.Assert;
import org.junit.Test;

public class OpenTelemetryServletFilterTest {

    @Test
    public void testParseJobUrlLatestBuildConsole(){
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", null, "/job/{job.fullName}/lastBuild/console");
        String pathInfo = "/job/my-war/job/master/lastBuild/console";
        verifyJobUrlParsing(expected, pathInfo);
    }

    /**
     * TODO trim trailing slash
     */
    @Test
    public void testParseJobUrlLastBuild(){
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", null, "/job/{job.fullName}/lastBuild/");
        String pathInfo = "/job/my-war/job/master/lastBuild/";
        verifyJobUrlParsing(expected, pathInfo);
    }



    @Test
    public void testParseJobUrlLastBuildNoTrailingSlash(){
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", null, "/job/{job.fullName}/lastBuild");
        String pathInfo = "/job/my-war/job/master/lastBuild";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobUrlBuildNoTrailingSlash(){
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", 3l, "/job/{job.fullName}/{run.number}");
        String pathInfo = "/job/my-war/job/master/3";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobUrlBuildConsole(){
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", 3l, "/job/{job.fullName}/{run.number}/console");
        String pathInfo = "/job/my-war/job/master/3/console";
        verifyJobUrlParsing(expected, pathInfo);
    }

    @Test
    public void testParseJobUrl(){
        OpenTelemetryServletFilter.ParsedJobUrl expected = new OpenTelemetryServletFilter.ParsedJobUrl(
            "my-war/master", null, "/job/{job.fullName}/");
        String pathInfo = "/job/my-war/job/master/";
        verifyJobUrlParsing(expected, pathInfo);
    }

    private void verifyJobUrlParsing(OpenTelemetryServletFilter.ParsedJobUrl expected, String pathInfo) {
        OpenTelemetryServletFilter.ParsedJobUrl actual = new OpenTelemetryServletFilter(OpenTelemetry.noop().getTracer("test")).parseJobUrl(pathInfo);
        System.out.println(actual);
        Assert.assertEquals(actual, expected);
    }
}
