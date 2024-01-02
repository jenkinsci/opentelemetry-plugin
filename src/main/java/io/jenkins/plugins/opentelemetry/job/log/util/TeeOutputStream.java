/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;

public class TeeOutputStream extends OutputStream {

    final OutputStream primary;
    final OutputStream secondary;

    public TeeOutputStream(OutputStream primary, OutputStream secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public void write(int b) throws IOException {
        primary.write(b);
        secondary.write(b);
    }

    @Override
    public void write(@Nonnull byte[] b) throws IOException {
        primary.write(b);
        secondary.write(b);
    }

    @Override
    public void write(@Nonnull byte[] b, int off, int len) throws IOException {
        primary.write(b, off, len);
        secondary.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        primary.flush();
        secondary.flush();
    }

    @Override
    public void close() throws IOException {
        primary.close();
        secondary.close();
    }
}
