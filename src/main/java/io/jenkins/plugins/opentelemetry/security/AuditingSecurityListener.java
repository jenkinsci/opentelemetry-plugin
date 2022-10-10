/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.EventBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;
import jenkins.security.SecurityListener;
import org.acegisecurity.userdetails.UserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO improve {@link io.jenkins.plugins.opentelemetry.init.ServletFilterInitializer} to ensure the
 * {@link AuditingSecurityListener} events ({@link #loggedIn(String)}, {@link #failedToLogIn(String)}...) are invoked
 * within a trace.
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class AuditingSecurityListener extends SecurityListener implements OtelComponent {

    private final static Logger LOGGER = Logger.getLogger(AuditingSecurityListener.class.getName());

    private LongCounter loginSuccessCounter;
    private LongCounter loginFailureCounter;
    private LongCounter loginCounter;

    private io.opentelemetry.api.logs.Logger otelLogger;

    @Override
    public void afterSdkInitialized(Meter meter, io.opentelemetry.api.logs.Logger otelLogger, Tracer tracer, ConfigProperties configProperties) {
        this.otelLogger = otelLogger;

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

        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins authentication events...");
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
                    .put(SemanticAttributes.NET_SOCK_PEER_ADDR, webAuthenticationDetails.getRemoteAddress());
                message += " from " + webAuthenticationDetails.getRemoteAddress();
            }
        }

        EventBuilder eventBuilder = otelLogger.eventBuilder("authentication");
        eventBuilder
            .setBody(message)
            .setAllAttributes(attributesBuilder.build())
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

        LogRecordBuilder logBuilder = otelLogger.logRecordBuilder();
        logBuilder
            .setBody(message)
            .setAllAttributes(attributesBuilder.build())
            .setContext(Context.current()) // note there is no span as long as we don't fix the registration of the OpenTelemetryServletFilter
            .setSeverity(Severity.INFO)
            .emit();
    }

    @Override
    protected void loggedOut(@NonNull String username) {
        super.loggedOut(username);
    }

    @Override
    public void beforeSdkShutdown() {

    }
}
