/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import java.io.Serial;

/** Thrown when a configured Jenkins credential cannot be resolved. */
public class CredentialsNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Creates an exception with no detail message. */
    public CredentialsNotFoundException() {}

    /**
     * Creates an exception with a detail message.
     *
     * @param message detail message
     */
    public CredentialsNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a detail message and cause.
     *
     * @param message detail message
     * @param cause root cause
     */
    public CredentialsNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception with a cause.
     *
     * @param cause root cause
     */
    public CredentialsNotFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates an exception with full suppression and stack trace controls.
     *
     * @param message detail message
     * @param cause root cause
     * @param enableSuppression whether suppression is enabled
     * @param writableStackTrace whether the stack trace should be writable
     */
    public CredentialsNotFoundException(
            String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
