package io.jenkins.plugins.opentelemetry.servlet;

import org.junit.Test;

import static org.junit.Assert.*;

public class RemoteSpanServletFilterTest {

    @Test
    public void isJenkinsRemoteBuildURL() {
        assertTrue("/job/test/build is remote build", RemoteSpanServletFilter.isJenkinsRemoteBuildURL("/job/test/build"));
        assertTrue("/job/parent/job/test/build is remote build", RemoteSpanServletFilter.isJenkinsRemoteBuildURL("/job/parent/job/test/build"));
        assertTrue("/job/parent/job/test/buildWithParameters is remote build", RemoteSpanServletFilter.isJenkinsRemoteBuildURL("/job/parent/job/test/buildWithParameters"));
        assertTrue("/jenkins/job/target-project/build is remote build", RemoteSpanServletFilter.isJenkinsRemoteBuildURL("/jenkins/job/target-project/build"));
        assertTrue("/jenkins/job/target_project-123/build is remote build", RemoteSpanServletFilter.isJenkinsRemoteBuildURL("/jenkins/job/target-project/build"));


    }
}