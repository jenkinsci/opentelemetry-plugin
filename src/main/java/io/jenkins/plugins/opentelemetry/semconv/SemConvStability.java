/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

public enum SemConvStability {
    JENKINS("Jenkins (custom conventions only)", true, false),
    JENKINS_OTEL_DUP("Jenkins + OpenTelemetry (both conventions)", true, true),
    OTEL("OpenTelemetry (standard conventions only)", false, true);

    private final String displayName;
    private final boolean emitLegacyCicdSemConv;
    private final boolean emitOtelCicdSemConv;

    SemConvStability(String displayName, boolean emitLegacyCicdSemConv, boolean emitOtelCicdSemConv) {
        this.displayName = displayName;
        this.emitLegacyCicdSemConv = emitLegacyCicdSemConv;
        this.emitOtelCicdSemConv = emitOtelCicdSemConv;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean emitLegacyCicdSemConv() {
        return emitLegacyCicdSemConv;
    }

    public boolean emitOtelCicdSemConv() {
        return emitOtelCicdSemConv;
    }

    public static String getDefaultValue() {
        return JENKINS.name();
    }
}
