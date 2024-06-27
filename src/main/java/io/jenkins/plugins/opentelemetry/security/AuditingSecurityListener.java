/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import io.jenkins.plugins.opentelemetry.JenkinsControllerOpenTelemetry;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.semconv.ClientAttributes;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import jenkins.YesNoMaybe;
import jenkins.security.SecurityListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO improve {@link io.jenkins.plugins.opentelemetry.init.ServletFilterInitializer} to ensure the
 * {@link AuditingSecurityListener} events ({@link #loggedIn(String)}, {@link #failedToLogIn(String)}...) are invoked
 * within a trace.
 */
@Extension(dynamicLoadable = YesNoMaybe.YES, optional = true)
public class AuditingSecurityListener extends SecurityListener  {

    private final static Logger LOGGER = Logger.getLogger(AuditingSecurityListener.class.getName());

    private LongCounter loginSuccessCounter;
    private LongCounter loginFailureCounter;
    private LongCounter loginCounter;

    private EventLogger eventLogger;

    private JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry;

    @PostConstruct
    public void postConstruct() {
        LOGGER.log(Level.FINE, () -> "Start monitoring Jenkins controller authentication events...");

        this.eventLogger = jenkinsControllerOpenTelemetry.getDefaultEventLogger();
        Meter meter = jenkinsControllerOpenTelemetry.getDefaultMeter();

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

    @Override
    protected void authenticated2(@NonNull UserDetails details) {
        super.authenticated2(details);
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
            .put(JenkinsOtelSemanticAttributes.EVENT_CATEGORY, JenkinsOtelSemanticAttributes.EventCategoryValues.AUTHENTICATION)
            .put(JenkinsOtelSemanticAttributes.EVENT_OUTCOME, JenkinsOtelSemanticAttributes.EventOutcomeValues.SUCCESS)
            .put(EnduserIncubatingAttributes.ENDUSER_ID, user.map(User::getId).orElse(username))
        ;

        // Stapler.getCurrentRequest() returns null, it's not yet initialized
        SecurityContext securityContext = SecurityContextHolder.getContext();
        if (securityContext != null) {
            Authentication authentication = securityContext.getAuthentication();
            Object details = authentication.getDetails();
            if (details instanceof WebAuthenticationDetails) {
                WebAuthenticationDetails webAuthenticationDetails = (WebAuthenticationDetails) details;
                attributesBuilder
                    .put(ClientAttributes.CLIENT_ADDRESS, webAuthenticationDetails.getRemoteAddress());
                message += " from " + webAuthenticationDetails.getRemoteAddress();
            }
        }
        attributesBuilder.put("message", message);

        eventLogger.builder("user_login")
            .setAttributes(attributesBuilder.build())
            .setSeverity(Severity.INFO)
            .emit();
    }


    @Override
    protected void failedToLogIn(@NonNull String username) {
        this.loginCounter.add(1);
        this.loginFailureCounter.add(1);

        String message = "Failed login of user '" + username + "'";
        AttributesBuilder attributesBuilder = Attributes.builder();
        attributesBuilder
            .put(JenkinsOtelSemanticAttributes.EVENT_CATEGORY, JenkinsOtelSemanticAttributes.EventCategoryValues.AUTHENTICATION)
            .put(JenkinsOtelSemanticAttributes.EVENT_OUTCOME, JenkinsOtelSemanticAttributes.EventOutcomeValues.FAILURE)
            .put(EnduserIncubatingAttributes.ENDUSER_ID, username)
        ;

        // TODO find a solution to retrieve the remoteIpAddress

        attributesBuilder.put("message", message);

        eventLogger.builder("user_login")
            .setAttributes(attributesBuilder.build())
            .setSeverity(Severity.WARN)
            .emit();
    }

    @Inject
    public void setJenkinsControllerOpenTelemetry(JenkinsControllerOpenTelemetry jenkinsControllerOpenTelemetry) {
        this.jenkinsControllerOpenTelemetry = jenkinsControllerOpenTelemetry;
    }
}
