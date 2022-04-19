/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.log;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.data.Severity;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

final class NoopLogBuilder implements LogBuilder {
    @Override
    public LogBuilder setEpoch(long timestamp, TimeUnit unit) {
        return this;
    }

    @Override
    public LogBuilder setEpoch(Instant instant) {
        return this;
    }

    @Override
    public LogBuilder setContext(Context context) {
        return this;
    }

    @Override
    public LogBuilder setSeverity(Severity severity) {
        return this;
    }

    @Override
    public LogBuilder setSeverityText(String severityText) {
        return this;
    }

    @Override
    public LogBuilder setBody(String body) {
        return this;
    }

    @Override
    public LogBuilder setAttributes(Attributes attributes) {
        return this;
    }

    @Override
    public void emit() {

    }
}
