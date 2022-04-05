/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.scm.api.SCMEvent;
import jenkins.security.SecurityListener;
import org.acegisecurity.userdetails.UserDetails;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Optional;

/**
 * TODO improve {@link io.jenkins.plugins.opentelemetry.init.ServletFilterInitializer} to ensure the
 * {@link AuditingSecurityListener} events ({@link #loggedIn(String)}, {@link #failedToLogIn(String)}...) are invoked
 * within a trace.
 */
@Extension
public class AuditingSecurityListener extends SecurityListener {
    private OpenTelemetrySdkProvider openTelemetrySdkProvider;
    private Meter meter;
    private LongCounter loginSuccessCounter;
    private LongCounter loginFailureCounter;
    private LongCounter loginCounter;

    private void initialise() {
        loginSuccessCounter =
            meter.counterBuilder(JenkinsSemanticMetrics.LOGIN_SUCCESS)
                .setDescription("Successful logins")
                .setUnit("1")
                .build();
        loginFailureCounter =
            meter.counterBuilder(JenkinsSemanticMetrics.LOGIN_FAILURE)
                .setDescription("Failing logins")
                .setUnit("1")
                .build();

        loginCounter =
            meter.counterBuilder(JenkinsSemanticMetrics.LOGIN)
                .setDescription("Logins")
                .setUnit("1")
                .build();
    }


    @Deprecated
    @Override
    protected void authenticated(@NonNull UserDetails details) {
        super.authenticated(details);
    }

    @Override
    protected void failedToAuthenticate(@NonNull String username) {
        super.failedToAuthenticate(username);
    }

    @Override
    protected void loggedIn(@NonNull String username) {
        this.loginCounter.add(1);
        this.loginSuccessCounter.add(1);
        String message = "Successful login of user '" + username + "'";
        AttributesBuilder attributesBuilder = Attributes.builder();
        Optional<User> user = Optional.ofNullable(User.current());
        attributesBuilder
            .put(SemanticAttributes.ENDUSER_ID, user.map(u -> u.getId()).orElse(username))
            .put(JenkinsOtelSemanticAttributes.EVENT_CATEGORY, JenkinsOtelSemanticAttributes.EventCategoryValues.AUTHENTICATION)
            .put(JenkinsOtelSemanticAttributes.EVENT_ACTION, "user_login")
            .put(JenkinsOtelSemanticAttributes.EVENT_OUTCOME, JenkinsOtelSemanticAttributes.EventOutcomeValues.SUCCESS);

        // Stapler.getCurrentRequest() returns null, it's not yet initialized
        SecurityContext securityContext = SecurityContextHolder.getContext();
        if (securityContext != null) {
            Authentication authentication = securityContext.getAuthentication();
            Object details = authentication.getDetails();
            if (details instanceof WebAuthenticationDetails) {
                WebAuthenticationDetails webAuthenticationDetails = (WebAuthenticationDetails) details;
                attributesBuilder
                    .put(SemanticAttributes.NET_PEER_IP, webAuthenticationDetails.getRemoteAddress());
                message += " from " + webAuthenticationDetails.getRemoteAddress();
            }
        }

        LogBuilder logBuilder = openTelemetrySdkProvider.getLogEmitter().logBuilder();
        logBuilder
            .setBody(message)
            .setAttributes(attributesBuilder.build())
            .setContext(Context.current()) // note there is no span as long as we don't fix the registration of the OpenTelemetryServletFilter
            .setSeverity(Severity.INFO)
            .emit();
    }

    @Override
    protected void userCreated(@NonNull String username) {
        super.userCreated(username);
    }

    @Override
    protected void failedToLogIn(@NonNull String username) {
        this.loginCounter.add(1);
        this.loginFailureCounter.add(1);

        String message = "Failed login of user '" + username + "'";
        AttributesBuilder attributesBuilder = Attributes.builder();
        attributesBuilder
            .put(SemanticAttributes.ENDUSER_ID, username)
            .put(JenkinsOtelSemanticAttributes.EVENT_CATEGORY, JenkinsOtelSemanticAttributes.EventCategoryValues.AUTHENTICATION)
            .put(JenkinsOtelSemanticAttributes.EVENT_ACTION, "user_login")
            .put(JenkinsOtelSemanticAttributes.EVENT_OUTCOME, JenkinsOtelSemanticAttributes.EventOutcomeValues.FAILURE);

        // TODO find a solution to retrieve the remoteIpAddress

        LogBuilder logBuilder = openTelemetrySdkProvider.getLogEmitter().logBuilder();
        logBuilder
            .setBody(message)
            .setAttributes(attributesBuilder.build())
            .setContext(Context.current()) // note there is no span as long as we don't fix the registration of the OpenTelemetryServletFilter
            .setSeverity(Severity.INFO)
            .emit();
    }

    @Override
    protected void loggedOut(@NonNull String username) {
        super.loggedOut(username);
    }

    @Inject
    public void setJenkinsOtelPlugin(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
        this.openTelemetrySdkProvider = openTelemetrySdkProvider;
        initialise();
    }
}
