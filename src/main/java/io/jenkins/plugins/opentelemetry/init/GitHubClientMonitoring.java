/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import com.google.common.base.Preconditions;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.authorization.AuthorizationProvider;
import org.kohsuke.github.authorization.UserAuthorizationProvider;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This implementation of the monitoring of the GitHub client relies on a hack with Java reflection to access a private
 * field of the {@link Connector} class because we have not found any public API to observe the state of this GitHub client.
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class GitHubClientMonitoring {
    private final static Logger logger = Logger.getLogger(GitHubClientMonitoring.class.getName());

    protected Meter meter;

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public void initialize() {
        final AttributeKey<String> GITHUB_API_URL = AttributeKey.stringKey("github.api.url");

        try {
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
            Field credentialsTokenProvider_credentialsField = credentialsTokenProviderClass.getDeclaredField("credentials");
            credentialsTokenProvider_credentialsField.setAccessible(true);

            meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_GITHUB_API_RATE_LIMIT_REMAINING_REQUESTS)
                .ofLongs()
                .setDescription("GitHub Repository API rate limit remaining requests")
                .setUnit("1")
                .buildWithCallback(gauge -> {
                    logger.log(Level.FINE, () -> "Collect GitHub client API rate limit metrics");
                    try {
                        Map<GitHub, ?> reverseLookup = (Map<GitHub, ?>) connector_reverseLookupField.get(null);
                        reverseLookup.keySet().forEach(gitHub -> {
                            GHRateLimit ghRateLimit = gitHub.lastRateLimit();
                            try {
                                AttributesBuilder attributesBuilder = Attributes.of(GITHUB_API_URL, gitHub.getApiUrl()).toBuilder();
                                String authentication;
                                if (gitHub.isAnonymous()) {
                                    authentication = "anonymous";
                                } else {
                                    Object gitHubClient = gitHub_clientField.get(gitHub);
                                    Preconditions.checkState(gitHubClientClass.isAssignableFrom(gitHubClient.getClass()));
                                    AuthorizationProvider authorizationProvider = (AuthorizationProvider) gitHubClient_authorizationProviderField.get(gitHubClient);
                                    if (authorizationProvider instanceof UserAuthorizationProvider) {
                                        String gitHubLogin = ((UserAuthorizationProvider) authorizationProvider).getLogin();
                                        attributesBuilder.put(SemanticAttributes.ENDUSER_ID, gitHubLogin);
                                        authentication = "login:" + gitHubLogin;
                                    } else if (credentialsTokenProviderClass.isAssignableFrom(authorizationProvider.getClass())) {
                                        GitHubAppCredentials credentials = (GitHubAppCredentials) credentialsTokenProvider_credentialsField.get(authorizationProvider);
                                        attributesBuilder.put("github.app.id", credentials.getAppID());
                                        attributesBuilder.put("github.app.owner", credentials.getOwner());
                                        authentication = "application:id=" + credentials.getAppID() + ",owner=" + credentials.getOwner();
                                    } else {
                                        authentication = authorizationProvider.getClass() + ":" + System.identityHashCode(authorizationProvider);
                                    }
                                }
                                Attributes attributes = attributesBuilder.put("github.authentication", authentication).build();
                                logger.log(Level.FINER, () -> "Collect GitHub API " + attributes + ": rateLimit.remaining:" + ghRateLimit.getRemaining());
                                gauge.record(ghRateLimit.getRemaining(), attributes);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
            logger.log(Level.FINE, "GitHub client monitoring initialized");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Jenkins doesn't support {@link com.google.inject.Provides} so we manually wire dependencies :-(
     */
    @Inject
    public void setMeter(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
    }
}
