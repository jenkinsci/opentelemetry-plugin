/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.log;

import io.opentelemetry.sdk.logs.LogBuilder;
import io.opentelemetry.sdk.logs.LogEmitter;

public class NoopLogEmitter implements LogEmitter {
    public static LogEmitter noop() {
        return new NoopLogEmitter();
    }
    private NoopLogEmitter(){

    }
    @Override
    public LogBuilder logBuilder() {
        return new NoopLogBuilder();
    }
}
