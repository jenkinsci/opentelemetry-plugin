/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import javax.annotation.Nullable;
import java.util.Objects;

public class OpenTelemetryConfiguration {

    private final String endpoint;
    private final boolean useTls;
    private final String authenticationTokenName;
    private final String authenticationTokenValueId;

    public OpenTelemetryConfiguration(@Nullable String endpoint, boolean useTls, @Nullable String authenticationTokenName, @Nullable String authenticationTokenValueId) {
        this.endpoint = endpoint;
        this.useTls = useTls;
        this.authenticationTokenName = authenticationTokenName;
        this.authenticationTokenValueId = authenticationTokenValueId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public String getAuthenticationTokenName() {
        return authenticationTokenName;
    }

    public String getAuthenticationTokenValueId() {
        return authenticationTokenValueId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenTelemetryConfiguration that = (OpenTelemetryConfiguration) o;
        return useTls == that.useTls && Objects.equals(endpoint, that.endpoint) && Objects.equals(authenticationTokenName, that.authenticationTokenName) && Objects.equals(authenticationTokenValueId, that.authenticationTokenValueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpoint, useTls, authenticationTokenName, authenticationTokenValueId);
    }

    @Override
    public String toString() {
        return "OpenTelemetryConfiguration{" +
                "endpoint='" + endpoint + '\'' +
                ", useTls=" + useTls +
                ", authenticationTokenName='" + authenticationTokenName + '\'' +
                ", authenticationTokenValueId='" + authenticationTokenValueId + '\'' +
                '}';
    }
}
