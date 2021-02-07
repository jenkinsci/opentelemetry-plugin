/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.collect.Maps;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.SpanBuilderMock;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.net.URL;
import java.util.Map;

public class ScmStepHandlerTest {

    @Test
    public void testHttpsGithubUrl() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://github.com/open-telemetry/opentelemetry-java");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(SemanticAttributes.HTTP_URL), Matchers.equalTo("https://github.com/open-telemetry/opentelemetry-java"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testSshGithubUrl() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("git@github.com:open-telemetry/opentelemetry-java.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(SemanticAttributes.NET_PEER_NAME), Matchers.equalTo("github.com"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testScpStyleSshGitUrl() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("user@example.com:open-telemetry/opentelemetry-java.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(SemanticAttributes.NET_PEER_NAME), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testScpStyleSshGitUrlWithoutUsername() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("example.com:open-telemetry/opentelemetry-java.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(SemanticAttributes.NET_PEER_NAME), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testSshGitUrl() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://user@example.com/project.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(SemanticAttributes.NET_PEER_NAME), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("project"));
    }

    @Test
    public void testSshGitUrlWithPort() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://user@example.com:2222/project.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(SemanticAttributes.NET_PEER_NAME), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("project"));
    }

    @Test
    public void testSshGitUrlWithoutUsername() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://example.com/project.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(SemanticAttributes.NET_PEER_NAME), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("project"));
    }

    @Test
    public void testHttpsGithubUrlWithSuffix() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://github.com/open-telemetry/opentelemetry-java.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();
        MatcherAssert.assertThat(attributes.get(SemanticAttributes.HTTP_URL), Matchers.equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testHttpsGithubUrlWithUsername() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://my_username@github.com/open-telemetry/opentelemetry-java.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();
        MatcherAssert.assertThat(attributes.get(SemanticAttributes.HTTP_URL), Matchers.equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testHttpsGithubUrlWithUsernamePassword() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://my_username:my_password@github.com/open-telemetry/opentelemetry-java.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();
        MatcherAssert.assertThat(attributes.get(SemanticAttributes.HTTP_URL), Matchers.equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    private SpanBuilderMock testGithubUrl(String githubUrl) throws Exception {
        SpanBuilderMock spanBuilder = new SpanBuilderMock("git");
        ScmStepHandler handler = new ScmStepHandler();
        Map<String, Object> arguments = Maps.newHashMap();
        arguments.put("url", githubUrl);

        handler.handle(arguments, spanBuilder);
        return spanBuilder;
    }

    @Test
    public void testSanitizeUserNamePassword() throws Exception {
        testSanitizeUrl("https://my_username:my_password@github.com/open-telemetry/opentelemetry-java.git", "https://github.com/open-telemetry/opentelemetry-java.git");
    }

    @Test
    public void testSanitizeUserName() throws Exception {
        testSanitizeUrl("https://my_username@github.com/open-telemetry/opentelemetry-java.git", "https://github.com/open-telemetry/opentelemetry-java.git");
    }

    private void testSanitizeUrl(String input, String expected) throws Exception {
        ScmStepHandler handler = new ScmStepHandler();
        String actual = handler.sanitizeUrl(new URL(input));
        MatcherAssert.assertThat(actual, Matchers.is(expected));
    }

    @Test
    public void testFileUrl() throws Exception {
        testSanitizeUrl("file:///srv/git/project.git", "file:///srv/git/project.git");
    }
}
