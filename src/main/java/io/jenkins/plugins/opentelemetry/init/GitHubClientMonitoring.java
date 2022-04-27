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
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
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
import org.kohsuke.github.extras.authorization.JWTTokenProvider;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.GitHubSemanticAttributes.*;
import static io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes.JENKINS_CREDENTIALS_ID;

/**
 * This implementation of the monitoring of the GitHub client relies on a hack with Java reflection to access a private
 * field of the {@link Connector} class because we have not found any public API to observe the state of this GitHub client.
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class GitHubClientMonitoring {
    private final static Logger logger = Logger.getLogger(GitHubClientMonitoring.class.getName());

    protected Meter meter;
    private final Field gitHub_clientField;
    private final Class<?> gitHubClientClass;
    private final Field gitHubClient_authorizationProviderField;
    private final Class<?> credentialsTokenProviderClass;
    private final Field credentialsTokenProvider_credentialsField;

    private final Field jwtTokenProvider_applicationIdField;

    private final Map<GitHub, ?> reverseLookup;

    public GitHubClientMonitoring() {
        try {
            Field connector_reverseLookupField = Connector.class.getDeclaredField("reverseLookup");
            connector_reverseLookupField.setAccessible(true);
            Preconditions.checkState(Modifier.isStatic(connector_reverseLookupField.getModifiers()), "Connector#reverseLookup is NOT a static field: %s", connector_reverseLookupField);

            gitHub_clientField = GitHub.class.getDeclaredField("client");
            gitHub_clientField.setAccessible(true);

            gitHubClientClass = Class.forName("org.kohsuke.github.GitHubClient");
            gitHubClient_authorizationProviderField = gitHubClientClass.getDeclaredField("authorizationProvider");
            gitHubClient_authorizationProviderField.setAccessible(true);

            credentialsTokenProviderClass = Class.forName("org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials$CredentialsTokenProvider");
            credentialsTokenProvider_credentialsField = credentialsTokenProviderClass.getDeclaredField("credentials");
            credentialsTokenProvider_credentialsField.setAccessible(true);
            Preconditions.checkState(GitHubAppCredentials.class.isAssignableFrom(credentialsTokenProvider_credentialsField.getType()),
                "Unsupported type for credentialsTokenProvider.credentials. Expected GitHubAppCredentials, current %s", credentialsTokenProvider_credentialsField);

            jwtTokenProvider_applicationIdField = JWTTokenProvider.class.getDeclaredField("applicationId");
            jwtTokenProvider_applicationIdField.setAccessible(true);

            reverseLookup = (Map<GitHub, ?>) connector_reverseLookupField.get(null);
        } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
            throw new RuntimeException("Unsupported version of the Github Branch Source Plugin", e);
        } catch (SecurityException e) {
            throw new RuntimeException("SecurityManager is activated, cannot monitor the GitHub Client as it requires Java reflection permissions", e);
        }
    }

    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public void initialize() {
        meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_GITHUB_API_RATE_LIMIT_REMAINING_REQUESTS)
            .ofLongs()
            .setDescription("GitHub Repository API rate limit remaining requests")
            .setUnit("1")
            .buildWithCallback(gauge -> {
                logger.log(Level.FINE, () -> "Collect GitHub client API rate limit metrics");
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
                                if (gitHubLogin == null) {
                                    gitHubLogin = gitHub.getMyself().getLogin();
                                }
                                attributesBuilder.put(SemanticAttributes.ENDUSER_ID, gitHubLogin);
                                authentication = "login:" + gitHubLogin;
                            } else if (credentialsTokenProviderClass.isAssignableFrom(authorizationProvider.getClass())) {
                                GitHubAppCredentials credentials = (GitHubAppCredentials) credentialsTokenProvider_credentialsField.get(authorizationProvider);
                                attributesBuilder.put(GITHUB_APP_ID, credentials.getAppID());
                                attributesBuilder.put(JENKINS_CREDENTIALS_ID, credentials.getId());
                                gitHub.getApp().getName();
                                authentication = "app:id=" + credentials.getAppID();
                                if (credentials.getOwner() != null) {
                                    attributesBuilder.put(GITHUB_APP_OWNER, credentials.getOwner());
                                    authentication += ",owner=" + credentials.getOwner();
                                }
                            } else if (authorizationProvider instanceof JWTTokenProvider) {
                                String applicationId = (String) jwtTokenProvider_applicationIdField.get(authorizationProvider);
                                attributesBuilder.put(GITHUB_APP_ID, applicationId);
                                authentication = "jwtToken:applicationId=" + applicationId;
                            } else {
                                authentication = authorizationProvider.getClass() + ":" + System.identityHashCode(authorizationProvider);
                            }
                        }
                        Attributes attributes = attributesBuilder.put(GITHUB_AUTHENTICATION, authentication).build();
                        logger.log(Level.FINER, () -> "Collect GitHub API " + attributes + ": rateLimit.remaining:" + ghRateLimit.getRemaining());
                        gauge.record(ghRateLimit.getRemaining(), attributes);
                    } catch (IllegalAccessException|IOException e) {
                        throw new RuntimeException(e);
                    }
                });

            });
        logger.log(Level.FINE, "GitHub client monitoring initialized");
    }

    /**
     * Jenkins doesn't support {@link com.google.inject.Provides} so we manually wire dependencies :-(
     */
    @Inject
    public void setMeter(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
    }
}
