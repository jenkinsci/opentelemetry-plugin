/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.jenkins;

import java.io.Serial;

public class CredentialsNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CredentialsNotFoundException() {
    }

    public CredentialsNotFoundException(String message) {
        super(message);
    }

    public CredentialsNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public CredentialsNotFoundException(Throwable cause) {
        super(cause);
    }

    public CredentialsNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
