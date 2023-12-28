/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class TeePrintStream extends PrintStream {

    final PrintStream secondary;

    public TeePrintStream(@NonNull PrintStream primary, @NonNull PrintStream secondary) {
        super(primary, false, StandardCharsets.UTF_8);
        this.secondary = secondary;
    }

    @Override
    public void flush() {
        super.flush();
        secondary.flush();
    }

    @Override
    public void close() {
        RuntimeException e1 = null;
        try {
            super.close();
        } catch (RuntimeException e) {
            e1 = e;
        }
        RuntimeException e2 = null;
        try {
            secondary.close();
        } catch (RuntimeException e) {
            e2 = e;
        }
        if (e1 != null && e2 != null) {
            throw new RuntimeException("Both print streams failed to close: primary=" + e1 + ", secondary=" + e2, e1);
        } else if (e1 != null) {
            throw e1;
        } else if (e2 != null) {
            throw e2;
        }
    }

    @Override
    public boolean checkError() {
        return super.checkError() && secondary.checkError();
    }

    @Override
    public void write(int b) {
        super.write(b);
        secondary.write(b);
    }

    @Override
    public void write(@NonNull byte[] buf, int off, int len) {
        super.write(buf, off, len);
        secondary.write(buf, off, len);
    }
}