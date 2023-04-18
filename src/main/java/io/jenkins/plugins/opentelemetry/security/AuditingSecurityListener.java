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
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import jenkins.YesNoMaybe;
import jenkins.security.SecurityListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import javax.annotation.Nonnull;
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

    private EventEmitter eventEmitter;

    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {
        this.eventEmitter = eventEmitter;

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

    @Override
    protected void authenticated2(@Nonnull UserDetails details) {
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
            .put(SemanticAttributes.ENDUSER_ID, user.map(User::getId).orElse(username))
        ;

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
        attributesBuilder.put("message", message);

        eventEmitter.emit("user_login", attributesBuilder.build());
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
            .put(JenkinsOtelSemanticAttributes.EVENT_CATEGORY, JenkinsOtelSemanticAttributes.EventCategoryValues.AUTHENTICATION)
            .put(JenkinsOtelSemanticAttributes.EVENT_OUTCOME, JenkinsOtelSemanticAttributes.EventOutcomeValues.FAILURE)
            .put(SemanticAttributes.ENDUSER_ID, username)
        ;

        // TODO find a solution to retrieve the remoteIpAddress

        attributesBuilder.put("message", message);

        eventEmitter.emit("user_login", attributesBuilder.build());
    }

    @Override
    protected void loggedOut(@NonNull String username) {
        super.loggedOut(username);
    }

    @Override
    public void beforeSdkShutdown() {

    }
}
