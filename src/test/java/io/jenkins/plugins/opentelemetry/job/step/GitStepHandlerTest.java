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
import org.eclipse.jgit.transport.URIish;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Map;

public class GitStepHandlerTest {

    @Test
    public void testHttpsGithubUrl() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://github.com/open-telemetry/opentelemetry-java", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(UrlAttributes.URL_FULL), Matchers.equalTo("https://github.com/open-telemetry/opentelemetry-java"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testSshGithubUrl() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("git@github.com:open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), Matchers.equalTo("github.com"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testScpStyleSshGitUrl() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("user@example.com:open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testScpStyleSshGitUrlWithoutUsername() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("example.com:open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testSshGitUrl() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://user@example.com/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("project"));
    }

    @Test
    public void testSshGitUrlWithPort() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://user@example.com:2222/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("project"));
    }

    @Test
    public void testSshGitUrlWithoutUsername() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_the_ssh_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("ssh://example.com/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(ServerAttributes.SERVER_ADDRESS), Matchers.equalTo("example.com"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("project"));
    }

    @Test
    public void testHttpsGithubUrlWithSuffix() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://github.com/open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();
        MatcherAssert.assertThat(attributes.get(UrlAttributes.URL_FULL), Matchers.equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testHttpsGithubUrlWithUsername() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://my_username@github.com/open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();
        MatcherAssert.assertThat(attributes.get(UrlAttributes.URL_FULL), Matchers.equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testHttpsGithubUrlWithUsernamePassword() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://my_username:my_password@github.com/open-telemetry/opentelemetry-java.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();
        MatcherAssert.assertThat(attributes.get(UrlAttributes.URL_FULL), Matchers.equalTo("https://github.com/open-telemetry/opentelemetry-java.git"));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));
    }

    @Test
    public void testFileGitUrl() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_local_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("file:///srv/git/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(UrlAttributes.URL_FULL), Matchers.is(Matchers.nullValue()));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("srv/git/project"));
    }

    @Test
    public void testFileGitUrlWithoutSchemeLinux() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_local_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("/srv/git/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(UrlAttributes.URL_FULL), Matchers.is(Matchers.nullValue()));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("srv/git/project"));
    }

    @Test
    public void testFileGitUrlWithoutSchemeWindows() throws Exception {
        // https://git-scm.com/book/en/v2/Git-on-the-Server-The-Protocols#_local_protocol
        SpanBuilderMock spanBuilder = testGithubUrl("c:\\srv/git/project.git", "master", "my-git-user");
        Map<AttributeKey<?>, Object> attributes = spanBuilder.getAttributes();

        MatcherAssert.assertThat(attributes.get(UrlAttributes.URL_FULL), Matchers.is(Matchers.nullValue()));
        MatcherAssert.assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_REPOSITORY), Matchers.equalTo("c:\\srv/git/project"));
    }

    private SpanBuilderMock testGithubUrl(@NonNull String githubUrl, @Nullable String gitBranch, @Nullable String gitUsername) throws Exception {

        GitStepHandler handler = new GitStepHandler();

        return (SpanBuilderMock) handler.createSpanBuilderFromGitDetails(githubUrl, gitBranch, gitUsername, "git", new TracerMock());
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
        GitStepHandler handler = new GitStepHandler();
        String actual = handler.sanitizeUrl(new URIish(input));
        MatcherAssert.assertThat(actual, Matchers.is(expected));
    }

    @Test
    public void testSanitizeFileUrl() throws Exception {
        testSanitizeUrl("file:///srv/git/project.git", "file:///srv/git/project.git");
    }
}