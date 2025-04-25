/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import java.security.Principal;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import org.apache.hc.client5.http.auth.BasicUserPrincipal;
import org.apache.hc.client5.http.auth.Credentials;

/**
 * Adapter to convert Jenkins credentials to Apache HTTP credentials.
 */
public class JenkinsCredentialsToApacheHttpCredentialsAdapter implements Credentials {
    Supplier<String> jenkinsCredentialsIdProvider;

    UsernamePasswordCredentials jenkinsUsernamePasswordCredentials;

    /**
     * @deprecated use {@link JenkinsCredentialsToApacheHttpCredentialsAdapter#JenkinsCredentialsToApacheHttpCredentialsAdapter(String)} instead
     */
    @Deprecated
    public JenkinsCredentialsToApacheHttpCredentialsAdapter(Supplier<String> jenkinsCredentialsIdProvider) {
        this.jenkinsCredentialsIdProvider = jenkinsCredentialsIdProvider;
    }

    public JenkinsCredentialsToApacheHttpCredentialsAdapter(String jenkinsCredentialsId) {
        this.jenkinsCredentialsIdProvider = () -> jenkinsCredentialsId;
    }

    @Override
    public Principal getUserPrincipal() throws CredentialsNotFoundException {
        return new BasicUserPrincipal(getJenkinsUsernamePasswordCredentials().getUsername());
    }

    @Override
    public char[] getPassword() throws CredentialsNotFoundException {
        return getJenkinsUsernamePasswordCredentials().getPassword().getPlainText().toCharArray();
    }

    UsernamePasswordCredentials getJenkinsUsernamePasswordCredentials() throws CredentialsNotFoundException {
        if (jenkinsUsernamePasswordCredentials == null) {
            String jenkinsCredentialsId = this.jenkinsCredentialsIdProvider.get();
            if (jenkinsCredentialsId == null || jenkinsCredentialsId.isEmpty()) {
                throw new CredentialsNotFoundException("No Jenkins credentials defined");
            }
            try {
                jenkinsUsernamePasswordCredentials = CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.get(),
                        ACL.SYSTEM, Collections.emptyList()),
                    CredentialsMatchers.withId(jenkinsCredentialsId));
            } catch (NoSuchElementException e) {
                throw new CredentialsNotFoundException("No Jenkins credentials found for id '" + jenkinsCredentialsId + "' and expected type 'UsernamePasswordCredentials'");
            }
            if (jenkinsUsernamePasswordCredentials == null) {
                throw new CredentialsNotFoundException("No Jenkins credentials found for id '" + jenkinsCredentialsId + "' and expected type 'UsernamePasswordCredentials'");
            }
        }
        return jenkinsUsernamePasswordCredentials;
    }

    @Override
    public String toString() {
        return "JenkinsCredentialsToApacheHttpCredentialsAdapter{" +
            "jenkinsUsernamePasswordCredentials=" + jenkinsUsernamePasswordCredentials +
            '}';
    }
}
