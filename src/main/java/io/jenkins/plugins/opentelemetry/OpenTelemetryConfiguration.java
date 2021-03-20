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
    private final int collectorTimeout;
    private final int exportInterval;
    private final String ignoredSteps;

    public OpenTelemetryConfiguration(@Nullable String endpoint, @Nullable String trustedCertificatesPem, @Nullable OtlpAuthentication authentication,
									  @Nullable int collectorTimeout, @Nullable int exportInterval, @Nullable String ignoredSteps) {
        this.endpoint = endpoint;
        this.trustedCertificatesPem = trustedCertificatesPem;
        this.authentication = authentication;
        this.collectorTimeout = collectorTimeout;
        this.exportInterval = exportInterval;
        this.ignoredSteps = ignoredSteps;
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
    public int getCollectorTimeout() {
        return collectorTimeout;
    }

    @Nonnull
    public int getExportInterval() {
        return exportInterval;
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
                Objects.equals(trustedCertificatesPem, that.trustedCertificatesPem) && Objects.equals(collectorTimeout, that.collectorTimeout) &&
                Objects.equals(exportInterval, that.exportInterval) && Objects.equals(ignoredSteps, that.ignoredSteps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, authentication, trustedCertificatesPem, collectorTimeout, exportInterval);
    }

    @Override
    public String toString() {
        return "OpenTelemetryConfiguration{" +
                "endpoint='" + endpoint + '\'' +
                ", trustedCertificatesPem=" + ((!(Strings.isNullOrEmpty(trustedCertificatesPem)))) +
                ", authentication=" + authentication +
                ", spanTimeout=" + collectorTimeout +
                ", metricTimeout=" + exportInterval +
				", ignoredSteps=" + ignoredSteps +
                '}';
    }
}
