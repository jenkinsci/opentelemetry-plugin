/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves, on a Jenkins agent, the classes of the log record export path while the remoting channel is open.
 * <p>
 * The agent loads the classes lazily from the controller, and the batch processor of the agent SDK first needs many
 * of them for the first record it exports: to copy the record ({@code toLogRecordData}, with the extended attributes
 * of {@code opentelemetry-api-incubator}), to marshal it and to send it. The OTLP exporter (plugin
 * {@code opentelemetry-api}) and OkHttp/Okio (plugin {@code okhttp-api}) are also loaded by two different agent class
 * loaders: a reference from one to the other is always resolved by a remote call to the controller
 * ({@code RemoteClassLoader.loadWithMultiClassLoader}), even when the jar was preloaded with
 * {@link hudson.remoting.Channel#preloadJar}. On a slow agent whose first log records are those of a short last step,
 * this first export only starts when the controller is already closing the channel: the class loading fails with
 * {@link NoClassDefFoundError} and the whole batch is lost. A flush does not help, the batch processor skips the
 * export when its queue is empty.
 * <p>
 * The warm-up emits two log records, built like the pipeline log records, through a throwaway logger provider whose
 * exporter sends them to an HTTP endpoint on the loopback interface of the agent. Nothing leaves the agent. The
 * resolved classes are then reused by the agent SDK.
 */
public final class AgentExportPathWarmUp {

    private static final Logger logger = Logger.getLogger(AgentExportPathWarmUp.class.getName());

    public static final boolean ENABLED = !Boolean.getBoolean(AgentExportPathWarmUp.class.getName() + ".disabled");

    static final long TIMEOUT_MILLIS = 10_000;

    private static final SpanContext WARM_UP_SPAN_CONTEXT = SpanContext.create(
            "0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.getSampled(), TraceState.getDefault());

    private AgentExportPathWarmUp() {}

    /**
     * Emit two log records through a throwaway logger provider that exports them to a loopback endpoint.
     * Never throws.
     *
     * @return the number of non-empty export requests received by the loopback endpoint
     */
    public static int run() {
        if (!ENABLED) {
            return 0;
        }
        long start = System.nanoTime();
        HttpServer server = null;
        SdkLoggerProvider loggerProvider = null;
        AtomicInteger requests = new AtomicInteger();
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try (InputStream body = exchange.getRequestBody()) {
                    if (body.readAllBytes().length > 0) {
                        requests.incrementAndGet();
                    }
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            OtlpHttpLogRecordExporter exporter = OtlpHttpLogRecordExporter.builder()
                    .setEndpoint(endpoint(server.getAddress()))
                    .setTimeout(Duration.ofMillis(TIMEOUT_MILLIS))
                    .build();
            loggerProvider = SdkLoggerProvider.builder()
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(exporter))
                    .build();
            io.opentelemetry.api.logs.Logger warmUpLogger = loggerProvider.get(AgentExportPathWarmUp.class.getName());
            Context context = Span.wrap(WARM_UP_SPAN_CONTEXT).storeInContext(Context.root());
            // one export per record, one after the other: the 2nd export reuses the connection and resolves what the
            // first did not need. Concurrent exports would leave the handling of the responses (okhttp3.ResponseBody)
            // to the OkHttp threads after the warm-up
            for (int i = 0; i < 2; i++) {
                warmUpLogger
                        .logRecordBuilder()
                        .setSeverity(Severity.INFO)
                        .setBody("warm-up")
                        .setAllAttributes(Attributes.builder()
                                .put(AttributeKey.stringKey("warm-up.string"), "warm-up")
                                .put(AttributeKey.longKey("warm-up.long"), i)
                                .build())
                        .setContext(context)
                        .setTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                        .emit();
                CompletableResultCode result = loggerProvider.forceFlush().join(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    logger.log(Level.FINE, "OpenTelemetry export path warm-up: export " + (i + 1) + " failed");
                }
            }
        } catch (IOException | RuntimeException | LinkageError e) {
            logger.log(Level.INFO, "Failure to warm up the OpenTelemetry log export path of the agent", e);
        } finally {
            if (loggerProvider != null) {
                try {
                    loggerProvider.shutdown().join(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (RuntimeException | LinkageError e) {
                    logger.log(Level.FINE, "Failure to shut down the warm-up logger provider", e);
                }
            }
            if (server != null) {
                server.stop(0);
            }
        }
        int received = requests.get();
        logger.log(
                Level.FINE,
                () -> "OpenTelemetry export path warm-up: " + received + " requests in "
                        + (System.nanoTime() - start) / 1_000_000 + "ms");
        return received;
    }

    /**
     * OTLP/HTTP log endpoint of the given address. IPv6 literals (e.g. the loopback address with
     * {@code -Djava.net.preferIPv6Addresses=true}) are enclosed in square brackets.
     */
    static String endpoint(InetSocketAddress address) {
        try {
            return new URI("http", null, address.getHostString(), address.getPort(), "/v1/logs", null, null).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid warm-up endpoint address " + address, e);
        }
    }
}
