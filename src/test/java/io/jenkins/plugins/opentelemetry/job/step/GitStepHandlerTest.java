/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.step;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.trace.SpanBuilderMock;
import io.opentelemetry.sdk.testing.trace.TracerMock;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import java.util.Map;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class GitStepHandlerTest {

    @Test
    void testHttpsGithubUrl() {
        SpanBuilderMock spanBuilder =
                testGithubUrl("https://github.com/open-telemetry/opentelemetry-java", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(
                attributes.get(UrlAttributes.URL_FULL),
                equalTo("https://github.com/open-telemetry/opentelemetry-java"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY),
                equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    void testSshGithubUrl() {
        SpanBuilderMock spanBuilder =
                testGithubUrl("git@github.com:open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), equalTo("github.com"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY),
                equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    void testScpStyleSshGitUrl() {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder =
                testGithubUrl("user@example.com:open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), equalTo("example.com"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY),
                equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    void testScpStyleSshGitUrlWithoutUsername() {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder =
                testGithubUrl("example.com:open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), equalTo("example.com"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY),
                equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    void testSshGitUrl() {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://user@example.com/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), equalTo("example.com"));
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), equalTo("project"));
    }

    @Test
    void testSshGitUrlWithPort() {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://user@example.com:2222/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), equalTo("example.com"));
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), equalTo("project"));
    }

    @Test
    void testSshGitUrlWithoutUsername() {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://example.com/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), equalTo("example.com"));
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), equalTo("project"));
    }

    @Test
    void testHttpsGithubUrlWithSuffix() {
        SpanBuilderMock spanBuilder =
                testGithubUrl("https://github.com/open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();
        assertThat(
                attributes.get(UrlAttributes.URL_FULL),
                equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY),
                equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    void testHttpsGithubUrlWithUsername() {
        SpanBuilderMock spanBuilder = testGithubUrl(
                "https://my_username@github.com/open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();
        assertThat(
                attributes.get(UrlAttributes.URL_FULL),
                equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY),
                equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    void testHttpsGithubUrlWithUsernamePassword() {
        SpanBuilderMock spanBuilder = testGithubUrl(
                "https://my_username:my_password@github.com/open-telemetry/opentelemetry-java.git",
                "master",
                "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();
        assertThat(
                attributes.get(UrlAttributes.URL_FULL),
                equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY),
                equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    void testFileGitUrl() {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_local_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("file:///srv/git/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(UrlAttributes.URL_FULL), is(nullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), equalTo("srv/git/project"));
    }

    @Test
    void testFileGitUrlWithoutSchemeLinux() {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_local_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("/srv/git/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(UrlAttributes.URL_FULL), is(nullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), equalTo("srv/git/project"));
    }

    @Test
    void testFileGitUrlWithoutSchemeWindows() {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_local_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("c:\\srv/git/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        assertThat(attributes.get(UrlAttributes.URL_FULL), is(nullValue()));
        assertThat(
                attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), equalTo("c:\\srv/git/project"));
    }

    private SpanBuilderMock testGithubUrl(
            @NonNull String githubUrl, @Nullable String gitBranch, @Nullable String gitUsername) {

        GitStepHandler handler = new GitStepHandler();

        return (SpanBuilderMock)
                handler.createSpanBuilderFromGitDetails(githubUrl, gitBranch, gitUsername, "git", new TracerMock());
    }

    @Test
    void testSanitizeUserNamePassword() throws Exception {
        testSanitizeUrl(
                "https://my_username:my_password@github.com/open-telemetry/opentelemetry-java.git",
                "https://github.com/open-telemetry/opentelemetry-java.git");
    }

    @Test
    void testSanitizeUserName() throws Exception {
        testSanitizeUrl(
                "https://my_username@github.com/open-telemetry/opentelemetry-java.git",
                "https://github.com/open-telemetry/opentelemetry-java.git");
    }

    private void testSanitizeUrl(String input, String expected) throws Exception {
        GitStepHandler handler = new GitStepHandler();
        String actual = handler.sanitizeUrl(new URIish(input));
        assertThat(actual, is(expected));
    }

    @Test
    void testSanitizeFileUrl() throws Exception {
        testSanitizeUrl("file:///srv/git/project.git", "file:///srv/git/project.git");
    }
}
