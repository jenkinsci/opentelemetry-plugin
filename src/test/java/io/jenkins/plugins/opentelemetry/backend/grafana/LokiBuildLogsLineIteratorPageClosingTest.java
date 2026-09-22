/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jenkins.plugins.opentelemetry.job.log.LogLine;
import io.jenkins.plugins.opentelemetry.job.log.util.CloseableIterator;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIterator;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIteratorInputStream;
import io.opentelemetry.api.OpenTelemetry;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.junit.jupiter.api.Test;

/**
 * Regression test: {@link LokiBuildLogsLineIterator} pages through Loki query results, each page holding its own
 * HTTP response stream backed by the shared, connection-pooled {@code httpClient}. Moving from an exhausted page to
 * the next one (or discarding the current page in {@link LokiBuildLogsLineIterator#skipLines(Long)}) must close the
 * page being left behind, or its underlying connection leaks.
 */
public class LokiBuildLogsLineIteratorPageClosingTest {

    @Test
    public void exhaustedPageIsClosedBeforeLoadingTheNextOne() throws Exception {
        TrackingCloseable page1Closeable = new TrackingCloseable();
        TrackingCloseable page2Closeable = new TrackingCloseable();
        Deque<Supplier<Iterator<LogLine<Long>>>> pages = new ArrayDeque<>(List.of(
                () -> new CloseableIterator<>(
                        List.of(new LogLine<>(1L, "line1")).iterator(), page1Closeable),
                () -> {
                    // page 1 must already be closed by the time page 2 is fetched: closing it only
                    // *eventually*, e.g. after page 2 is loaded, would still hold the pooled connection open
                    // for longer than necessary and defeats the point of closing eagerly.
                    assertTrue(
                            page1Closeable.closed,
                            "page 1 must be closed before page 2 is loaded, not merely at some later point");
                    return new CloseableIterator<>(Collections.emptyIterator(), page2Closeable);
                }));

        try (TestableLokiBuildLogsLineIterator iterator = new TestableLokiBuildLogsLineIterator(pages)) {
            assertTrue(iterator.hasNext());
            iterator.next(); // consume the only line of page 1

            assertFalse(page1Closeable.closed, "page 1 must not be closed while it may still be in use");

            // page 1 is now exhausted; asking for more must close it before loading page 2
            assertFalse(iterator.hasNext());
            assertTrue(page1Closeable.closed, "page 1 must be closed once exhausted and replaced by page 2");
        }
    }

    @Test
    public void currentPageIsClosedWhenSkipLinesDiscardsIt() throws Exception {
        TrackingCloseable page1Closeable = new TrackingCloseable();
        Deque<Supplier<Iterator<LogLine<Long>>>> pages = new ArrayDeque<>(List.of(() ->
                new CloseableIterator<>(List.of(new LogLine<>(1L, "line1")).iterator(), page1Closeable)));

        try (TestableLokiBuildLogsLineIterator iterator = new TestableLokiBuildLogsLineIterator(pages)) {
            assertTrue(iterator.hasNext()); // loads page 1
            assertFalse(page1Closeable.closed);

            iterator.skipLines(1L);

            assertTrue(page1Closeable.closed, "the discarded page must be closed by skipLines()");
        }
    }

    @Test
    public void closingTheReturnedLogStreamClosesTheLastPage() throws Exception {
        TrackingCloseable page1Closeable = new TrackingCloseable();
        Deque<Supplier<Iterator<LogLine<Long>>>> pages = new ArrayDeque<>(List.of(() ->
                new CloseableIterator<>(List.of(new LogLine<>(1L, "line1")).iterator(), page1Closeable)));

        TestableLokiBuildLogsLineIterator iterator = new TestableLokiBuildLogsLineIterator(pages);
        // LogLineIteratorInputStream is what LokiLogStorageRetriever actually hands back to callers as the log
        // stream; only LogLineIteratorInputStream.close() is invoked by consumers, never the iterator directly.
        InputStream logStream = new LogLineIteratorInputStream<>(
                iterator,
                new LogLineIterator.JenkinsHttpSessionLineBytesToLogLineIdMapper<>(null, 0, null),
                OpenTelemetry.noop().getTracer("io.jenkins"));

        assertTrue(logStream.read() >= 0, "the single log line must be readable before the stream is closed");
        assertFalse(page1Closeable.closed, "page 1 must not be closed while the stream is still open");

        logStream.close();

        assertTrue(
                page1Closeable.closed,
                "closing the returned log stream must close the last page, otherwise its pooled connection leaks");
    }

    private static class TrackingCloseable implements Closeable {
        boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }

    /**
     * Serves pre-built pages instead of querying a real Loki server.
     */
    private static class TestableLokiBuildLogsLineIterator extends LokiBuildLogsLineIterator {
        private final Deque<Supplier<Iterator<LogLine<Long>>>> pages;

        TestableLokiBuildLogsLineIterator(Deque<Supplier<Iterator<LogLine<Long>>>> pages) {
            super(
                    new LokiGetJenkinsBuildLogsQueryParametersBuilder()
                            .setJobFullName("my-war/master")
                            .setRunNumber(384)
                            .setTraceId("69a627b7bc02241b6029bed20f4ff8d8")
                            .setStartTime(Instant.EPOCH)
                            .setEndTime(Instant.EPOCH.plusSeconds(600))
                            .setServiceName("jenkins")
                            .setServiceNamespace("jenkins")
                            .build(),
                    NOOP_HTTP_CLIENT,
                    HttpClientContext.create(),
                    "http://localhost:3100",
                    Optional.empty(),
                    Optional.empty(),
                    OpenTelemetry.noop().getTracer("io.jenkins"));
            this.pages = pages;
        }

        @Override
        protected Iterator<LogLine<Long>> loadNextLogLines() {
            return pages.isEmpty()
                    ? Collections.emptyIterator()
                    : pages.removeFirst().get();
        }

        // The real client is never used since loadNextLogLines() is overridden, but the constructor requires
        // a non-null instance.
        private static final CloseableHttpClient NOOP_HTTP_CLIENT =
                HttpClients.custom().build();
    }
}
