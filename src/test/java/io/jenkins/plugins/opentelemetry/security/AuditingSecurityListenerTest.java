/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import hudson.ExtensionList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Regression test for {@link AuditingSecurityListener#loggedIn(String)}: {@code SecurityListener.fireLoggedIn(...)}
 * can be invoked by a {@code SecurityRealm} before the {@link SecurityContextHolder}'s authentication has been
 * populated for the current thread, so {@code loggedIn} must not assume it is non-null.
 */
@WithJenkins
class AuditingSecurityListenerTest {

    private AuditingSecurityListener listener;

    @BeforeEach
    void beforeEach(JenkinsRule j) {
        listener = ExtensionList.lookupSingleton(AuditingSecurityListener.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void afterEach() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loggedInDoesNotThrowWhenSecurityContextHasNoAuthentication() {
        assertDoesNotThrow(() -> listener.loggedIn("test-user"));
    }
}
