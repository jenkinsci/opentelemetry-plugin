/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class InstrumentationScope {
    @Nonnull
    final String instrumentationScopeName;
    @Nullable
    final String schemaUrl;
    @Nullable
    final String instrumentationScopeVersion;

    public InstrumentationScope(String instrumentationScopeName, @Nullable String schemaUrl, @Nullable String instrumentationScopeVersion) {
        this.instrumentationScopeName = Objects.requireNonNull(instrumentationScopeName);
        this.schemaUrl = schemaUrl;
        this.instrumentationScopeVersion = instrumentationScopeVersion;
    }

    public InstrumentationScope(@Nonnull String instrumentationScopeName) {
        this.instrumentationScopeName = instrumentationScopeName;
        this.schemaUrl = null;
        this.instrumentationScopeVersion = null;
    }

    @Override
    public String toString() {
        return "InstrumentationScope{" +
            "instrumentationScopeName='" + instrumentationScopeName + '\'' +
            ", schemaUrl='" + schemaUrl + '\'' +
            ", instrumentationScopeVersion='" + instrumentationScopeVersion + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstrumentationScope that = (InstrumentationScope) o;
        return Objects.equals(instrumentationScopeName, that.instrumentationScopeName) && Objects.equals(schemaUrl, that.schemaUrl) && Objects.equals(instrumentationScopeVersion, that.instrumentationScopeVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrumentationScopeName, schemaUrl, instrumentationScopeVersion);
    }
}