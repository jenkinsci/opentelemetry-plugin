/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.google.common.base.Strings;
import io.jenkins.plugins.opentelemetry.authentication.OtlpAuthentication;
import io.jenkins.plugins.opentelemetry.authentication.NoAuthentication;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class OpenTelemetryConfiguration {

    private final String endpoint;
    private final String trustedCertificatesPem;
    private final OtlpAuthentication authentication;
    private final int timeoutMillis;
    private final int exportIntervalMillis;

    public OpenTelemetryConfiguration(@Nullable String endpoint, @Nullable String trustedCertificatesPem, @Nullable OtlpAuthentication authentication,
                                      @Nullable int timeoutMillis, @Nullable int exportIntervalMillis) {
        this.endpoint = endpoint;
        this.trustedCertificatesPem = trustedCertificatesPem;
        this.authentication = authentication;
        this.timeoutMillis = timeoutMillis;
        this.exportIntervalMillis = exportIntervalMillis;
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

    @Nullable
    public String getTrustedCertificatesPem() {
        return trustedCertificatesPem;
    }

    @Nonnull
    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    @Nonnull
    public int getExportIntervalMillis() {
        return exportIntervalMillis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenTelemetryConfiguration that = (OpenTelemetryConfiguration) o;
        return Objects.equals(endpoint, that.endpoint) && Objects.equals(authentication, that.authentication) &&
                Objects.equals(trustedCertificatesPem, that.trustedCertificatesPem) && Objects.equals(timeoutMillis, that.timeoutMillis) &&
                Objects.equals(exportIntervalMillis, that.exportIntervalMillis) ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, authentication, trustedCertificatesPem, timeoutMillis, exportIntervalMillis);
    }

    @Override
    public String toString() {
        return "OpenTelemetryConfiguration{" +
                "endpoint='" + endpoint + '\'' +
                ", trustedCertificatesPem=" + ((!(Strings.isNullOrEmpty(trustedCertificatesPem)))) +
                ", authentication=" + authentication +
                ", timeoutMillis=" + timeoutMillis +
                ", exportIntervalMillis=" + exportIntervalMillis +
                '}';
    }
}
