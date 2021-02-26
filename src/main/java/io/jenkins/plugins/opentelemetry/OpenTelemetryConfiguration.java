/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class OpenTelemetryConfiguration {

    private final String endpoint;
    private final OtlpAuthentication authentication;

    public OpenTelemetryConfiguration(@Nullable String endpoint, @Nullable OtlpAuthentication authentication) {
        this.endpoint = endpoint;
        this.authentication = authentication;
    }

    @Nullable
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @return default to {@link NoAuthentication}
     */
    @Nonnull
    public OtlpAuthentication getAuthentication() {
        return authentication == null ? new NoAuthentication() : authentication;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenTelemetryConfiguration that = (OpenTelemetryConfiguration) o;
        return Objects.equals(endpoint, that.endpoint) && Objects.equals(authentication, that.authentication);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, authentication);
    }

    @Override
    public String toString() {
        return "OpenTelemetryConfiguration{" +
                "endpoint='" + endpoint + '\'' +
                ", authentication=" + authentication +
                '}';
    }
}
