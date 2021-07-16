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
    private final int exporterTimeoutMillis;
    private final int exporterIntervalMillis;
    private final String ignoredSteps;
    private final String serviceName;
    private final String serviceNamespace;

    public OpenTelemetryConfiguration(@Nullable String endpoint, @Nullable String trustedCertificatesPem, @Nullable OtlpAuthentication authentication,
                                      @Nullable int exporterTimeoutMillis, @Nullable int exporterIntervalMillis, @Nullable String ignoredSteps,
                                      @Nullable String serviceName, @Nullable String serviceNamespace) {
        this.endpoint = endpoint;
        this.trustedCertificatesPem = trustedCertificatesPem;
        this.authentication = authentication;
        this.exporterTimeoutMillis = exporterTimeoutMillis;
        this.exporterIntervalMillis = exporterIntervalMillis;
        this.ignoredSteps = ignoredSteps;
        this.serviceName = serviceName;
        this.serviceNamespace = serviceNamespace;
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
    public int getExporterTimeoutMillis() {
        return exporterTimeoutMillis;
    }

    @Nonnull
    public int getExporterIntervalMillis() {
        return exporterIntervalMillis;
    }

    @Nullable
    public String getIgnoredSteps() {
        return ignoredSteps;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenTelemetryConfiguration that = (OpenTelemetryConfiguration) o;
        return Objects.equals(endpoint, that.endpoint) && Objects.equals(authentication, that.authentication) &&
                Objects.equals(trustedCertificatesPem, that.trustedCertificatesPem) && Objects.equals(exporterTimeoutMillis, that.exporterTimeoutMillis) &&
                Objects.equals(exporterIntervalMillis, that.exporterIntervalMillis) && Objects.equals(ignoredSteps, that.ignoredSteps) &&
                Objects.equals(serviceName, that.serviceName) && Objects.equals(serviceNamespace, that.serviceNamespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, authentication, trustedCertificatesPem, exporterTimeoutMillis, exporterIntervalMillis, serviceName, serviceNamespace);
    }

    @Override
    public String toString() {
        return "OpenTelemetryConfiguration{" +
                "endpoint='" + endpoint + '\'' +
                ", trustedCertificatesPem=" + ((!(Strings.isNullOrEmpty(trustedCertificatesPem)))) +
                ", authentication=" + authentication +
                ", exporterTimeoutMillis=" + exporterTimeoutMillis +
                ", exporterIntervalMillis=" + exporterIntervalMillis +
                ", ignoredSteps=" + ignoredSteps +
                ", serviceName=" + serviceName +
                ", serviceNamespace=" + serviceNamespace +
                '}';
    }
}
