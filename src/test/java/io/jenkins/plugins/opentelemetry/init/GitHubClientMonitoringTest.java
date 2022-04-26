/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import junit.framework.TestCase;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.junit.Test;
import org.kohsuke.github.GitHub;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class GitHubClientMonitoringTest extends TestCase {

    @Test
    public void testIntrospectionCode() throws Exception {
        Field connector_reverseLookupField = Connector.class.getDeclaredField("reverseLookup");
        connector_reverseLookupField.setAccessible(true);
        if (!Modifier.isStatic(connector_reverseLookupField.getModifiers())) {
            throw new IllegalStateException("Connector#reverseLookup is NOT a static field: " + connector_reverseLookupField);
        }

        Field gitHub_clientField = GitHub.class.getDeclaredField("client");
        gitHub_clientField.setAccessible(true);

        Class gitHubClientClass = Class.forName("org.kohsuke.github.GitHubClient");
        Field gitHubClient_authorizationProviderField = gitHubClientClass.getDeclaredField("authorizationProvider");
        gitHubClient_authorizationProviderField.setAccessible(true);

        Class credentialsTokenProviderClass = Class.forName("org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials$CredentialsTokenProvider");
        Field credentialsTokenProvider_credentialsField =  credentialsTokenProviderClass.getDeclaredField("credentials");
        credentialsTokenProvider_credentialsField.setAccessible(true);
    }
}