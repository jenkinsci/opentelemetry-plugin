/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.kohsuke.github.GHRateLimit;
import org.kohsuke.github.GitHub;

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
            Field connectorReverseLookupField = Connector.class.getDeclaredField("reverseLookup");
            connectorReverseLookupField.setAccessible(true);

            if (!Modifier.isStatic(connectorReverseLookupField.getModifiers())) {
                throw new IllegalStateException("Connector#reverseLookup is NOT a static field: " + connectorReverseLookupField);
            }

            meter.gaugeBuilder(JenkinsSemanticMetrics.JENKINS_GITHUB_API_RATE_LIMIT_REMAINING_REQUESTS)
                .ofLongs()
                .setDescription("GitHub Repository API rate limit remaining requests")
                .setUnit("1")
                .buildWithCallback(gauge -> {
                    logger.log(Level.FINE, () -> "Collect GitHub client API rate limit metrics");
                    try {
                        Map<GitHub, ?> reverseLookup = (Map<GitHub, ?>) connectorReverseLookupField.get(null);
                        reverseLookup.keySet().forEach(gitHub -> {
                            GHRateLimit ghRateLimit = gitHub.lastRateLimit();
                            try {
                                String login = gitHub.isAnonymous() ? "#annonymous#" : gitHub.getMyself().getLogin();
                                logger.log(Level.FINER, () -> "Collect GitHub API " + gitHub.getApiUrl() + ", login: " +
                                    login + ": rateLimit.remaining:" + ghRateLimit.getRemaining());
                                gauge.record(
                                    ghRateLimit.getRemaining(),
                                    Attributes.of(
                                        GITHUB_API_URL, gitHub.getApiUrl(),
                                        SemanticAttributes.ENDUSER_ID, login));
                            } catch (IOException e) {
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
