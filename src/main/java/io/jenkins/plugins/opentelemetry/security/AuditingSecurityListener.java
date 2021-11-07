/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.jenkins.plugins.opentelemetry.OpenTelemetrySdkProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import jenkins.security.SecurityListener;
import org.acegisecurity.userdetails.UserDetails;

import javax.annotation.Nonnull;
import javax.inject.Inject;

@Extension
public class AuditingSecurityListener extends SecurityListener  {
    private Meter meter;
    private LongCounter loginSuccessCounter;
    private LongCounter loginFailureCounter;
    private LongCounter loginCounter;

    private void initialise(){
        loginSuccessCounter =
                meter.counterBuilder("login_success")
                .setDescription("Successful logins")
                .setUnit("1")
                .build();
        loginFailureCounter =
                meter.counterBuilder("login_failure")
                        .setDescription("Failing logins")
                        .setUnit("1")
                        .build();

        loginCounter =
                meter.counterBuilder("login")
                        .setDescription("Logins")
                        .setUnit("1")
                        .build();
    }



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
    }

    @Override
    protected void userCreated(@NonNull String username) {
        super.userCreated(username);
    }

    @Override
    protected void failedToLogIn(@NonNull String username) {
        this.loginCounter.add(1);
        this.loginFailureCounter.add(1);    }

    @Override
    protected void loggedOut(@NonNull String username) {
        super.loggedOut(username);
    }

    @Inject
    public void setJenkinsOtelPlugin(@Nonnull OpenTelemetrySdkProvider openTelemetrySdkProvider) {
        this.meter = openTelemetrySdkProvider.getMeter();
        initialise();
    }
}
