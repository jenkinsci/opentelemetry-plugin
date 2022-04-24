/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.lang.reflect.Field;
import java.util.Properties;

public class GitHubClientMonitoringTest extends TestCase {

    @Test
    public void testIntrospectionCode() throws Exception {
        final Field gitHubClientLoginField = Class.forName("org.kohsuke.github.GitHubClient").getDeclaredField("login");
        gitHubClientLoginField.setAccessible(true);

        final Field githubGithubClientField = GitHub.class.getDeclaredField("client");
        githubGithubClientField.setAccessible(true);

        GitHub gitHub = GitHubBuilder.fromProperties(new Properties()).build();
        Object githubClient = githubGithubClientField.get(gitHub);
        String login = (String) gitHubClientLoginField.get(githubClient);
        Assert.assertNull(login);
    }

}