/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Useful when the {@link Iterator} is backed by an {@link InputStream} and the logic of the code dereferences the
 * InputStream, make the {@link Iterator} closeable.
 * <br/>
 * TODO verify that extending {@link AutoCloseable} instead of {@link Closeable} is a good decision? The rationale
 * is that it's more general.
 */
public class CloseableIterator<E> implements Iterator<E>, AutoCloseable {
    private final Iterator<E> delegate;
    private final Closeable closeable;

    public CloseableIterator(Iterator<E> delegate, Closeable closeable) {
        this.delegate = delegate;
        this.closeable = closeable;
    }

    @Override
    public void close() throws Exception {
        if (delegate instanceof AutoCloseable) {
            ((AutoCloseable) delegate).close();
        }
        closeable.close();
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public E next() {
        return delegate.next();
    }
}
