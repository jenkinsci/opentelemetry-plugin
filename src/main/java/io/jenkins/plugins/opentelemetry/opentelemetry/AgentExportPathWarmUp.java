/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves, on a Jenkins agent, the classes of the OTLP/HTTP log export path while the remoting channel is open.
 * <p>
 * The OTLP exporter (plugin {@code opentelemetry-api}) and OkHttp/Okio (plugin {@code okhttp-api}) are loaded by two
 * different agent class loaders. A reference from one to the other is always resolved by a remote call to the
 * controller ({@code RemoteClassLoader.loadWithMultiClassLoader}), even when the jar was preloaded with
 * {@link hudson.remoting.Channel#preloadJar}. These references are first resolved by the first export that sends a
 * request ({@code okhttp3.Call}, {@code okhttp3.Request$Builder}, {@code okio.BufferedSink},
 * {@code okhttp3.ResponseBody}...). The batch processor skips the export when its queue is empty, so a flush does
 * not resolve them. On a slow agent, the first export may only start when the controller is already closing the
 * channel: the class loading fails with {@link NoClassDefFoundError} and the whole batch is lost.
 * <p>
 * The warm-up sends two empty export requests, with an exporter of the same classes, to an HTTP endpoint on the
 * loopback interface of the agent. Nothing leaves the agent. The resolved classes are then reused by the exporter
 * of the agent SDK.
 */
public final class AgentExportPathWarmUp {

    private static final Logger logger = Logger.getLogger(AgentExportPathWarmUp.class.getName());

    public static final boolean ENABLED = !Boolean.getBoolean(AgentExportPathWarmUp.class.getName() + ".disabled");

    static final long TIMEOUT_MILLIS = 10_000;

    private AgentExportPathWarmUp() {}

    /**
     * Send two empty OTLP/HTTP log export requests to a loopback endpoint. Never throws.
     *
     * @return the number of export requests received by the loopback endpoint
     */
    public static int run() {
        if (!ENABLED) {
            return 0;
        }
        long start = System.nanoTime();
        HttpServer server = null;
        LogRecordExporter exporter = null;
        AtomicInteger requests = new AtomicInteger();
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                try (InputStream body = exchange.getRequestBody()) {
                    body.readAllBytes();
                }
                requests.incrementAndGet();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            exporter = OtlpHttpLogRecordExporter.builder()
                    .setEndpoint(endpoint(server.getAddress()))
                    .setTimeout(Duration.ofMillis(TIMEOUT_MILLIS))
                    .build();
            // the 2nd export reuses the connection and resolves the classes the first one did not need
            for (int i = 0; i < 2; i++) {
                CompletableResultCode result =
                        exporter.export(Collections.emptyList()).join(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (!result.isSuccess()) {
                    logger.log(Level.FINE, "OpenTelemetry export path warm-up: export " + (i + 1) + " failed");
                }
            }
        } catch (IOException | RuntimeException | LinkageError e) {
            logger.log(Level.INFO, "Failure to warm up the OpenTelemetry log export path of the agent", e);
        } finally {
            if (exporter != null) {
                try {
                    exporter.shutdown().join(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (RuntimeException | LinkageError e) {
                    logger.log(Level.FINE, "Failure to shut down the warm-up exporter", e);
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
