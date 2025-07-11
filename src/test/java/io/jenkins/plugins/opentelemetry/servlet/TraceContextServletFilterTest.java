package io.jenkins.plugins.opentelemetry.servlet;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TraceContextServletFilterTest {

    @Test
    public void test_isJenkinsRemoteBuildURL() {
        test_isJenkinsRemoteBuildURL("/job/test/build");
        test_isJenkinsRemoteBuildURL("/job/parent/job/test/build");
        test_isJenkinsRemoteBuildURL("/job/parent/job/test/buildWithParameters");
        test_isJenkinsRemoteBuildURL("/jenkins/job/target-project/build");
        test_isJenkinsRemoteBuildURL("/jenkins/job/target_project-123/build");
    }

    private static void test_isJenkinsRemoteBuildURL(String uri) {
        assertTrue(
                uri + " is remote build",
                TraceContextServletFilter.JENKINS_TRIGGER_BUILD_URL_PATTERN
                        .matcher(uri)
                        .matches());
    }
}
