/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.remoting.Channel;
import hudson.remoting.Which;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Copies to a Jenkins agent the jars of the classes it needs to export its log records, before its OpenTelemetry SDK
 * is configured.
 * <p>
 * The agent loads the plugin classes lazily from the controller, one remote call per class. The OTLP exporter
 * resolves some of its classes only at its first or second export (e.g. the OTLP marshalers, the exporter metrics),
 * and the last export of an ephemeral agent (e.g. a Kubernetes pod) runs while the remoting channel is being closed:
 * the class loading fails and the last log records are lost. The classes of a preloaded jar are loaded from the
 * agent's local copy of the jar, without the remoting channel.
 */
public final class AgentExportClassesPreloader {

    private static final Logger logger = Logger.getLogger(AgentExportClassesPreloader.class.getName());

    public static final boolean ENABLED =
            !Boolean.getBoolean(AgentExportClassesPreloader.class.getName() + ".disabled");

    /**
     * One class per jar of the log export path, resolved with the class loader of the OpenTelemetry SDK so that each
     * jar is sent to the agent class loader that loads its classes.
     */
    @VisibleForTesting
    static final List<String> CLASSES_IN_EXPORT_JARS = List.of(
            "io.opentelemetry.api.OpenTelemetry",
            "io.opentelemetry.api.incubator.common.ExtendedAttributes",
            "io.opentelemetry.context.Context",
            "io.opentelemetry.common.ComponentLoader",
            "io.opentelemetry.sdk.OpenTelemetrySdk",
            "io.opentelemetry.sdk.common.CompletableResultCode",
            "io.opentelemetry.sdk.logs.SdkLoggerProvider",
            "io.opentelemetry.exporter.internal.http.HttpExporter",
            "io.opentelemetry.exporter.internal.otlp.logs.LogsRequestMarshaler",
            "io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter",
            "io.opentelemetry.exporter.sender.okhttp.internal.OkHttpHttpSender",
            "okhttp3.OkHttpClient",
            "okio.Buffer",
            "kotlin.Unit");

    private AgentExportClassesPreloader() {}

    /**
     * Preload the jars on the agent at the other end of the given channel, one at a time. Never throws.
     */
    public static void preload(@NonNull Channel channel) {
        long start = System.nanoTime();
        int preloaded = 0;
        for (Map.Entry<ClassLoader, Set<File>> entry : jarsByClassLoader().entrySet()) {
            for (File jar : entry.getValue()) {
                try {
                    channel.preloadJar(entry.getKey(), jar.toURI().toURL());
                    preloaded++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException | RuntimeException e) {
                    logger.log(
                            Level.INFO,
                            e,
                            () -> "Failure to preload the OpenTelemetry export jar " + jar + " on agent "
                                    + channel.getName());
                }
            }
        }
        int preloadedJars = preloaded;
        logger.log(
                Level.FINE,
                () -> "Preloaded " + preloadedJars + " export jars on agent " + channel.getName() + " in "
                        + (System.nanoTime() - start) / 1_000_000 + "ms");
    }

    /**
     * The jars of the classes of {@link #CLASSES_IN_EXPORT_JARS} and of this plugin, grouped by the class loader that
     * loads their classes. Classes that are not found or not loaded from a jar (e.g. in tests) are skipped.
     */
    @VisibleForTesting
    static Map<ClassLoader, Set<File>> jarsByClassLoader() {
        List<Class<?>> classes = new ArrayList<>();
        classes.add(AgentExportClassesPreloader.class);
        ClassLoader sdkClassLoader = OpenTelemetrySdk.class.getClassLoader();
        for (String className : CLASSES_IN_EXPORT_JARS) {
            try {
                classes.add(Class.forName(className, false, sdkClassLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                logger.log(Level.FINE, () -> "Class " + className + " not found, its jar is not preloaded");
            }
        }
        Map<ClassLoader, Set<File>> jarsByClassLoader = new LinkedHashMap<>();
        for (Class<?> clazz : classes) {
            try {
                File jar = Which.jarFile(clazz);
                if (jar.isFile() && jar.getName().endsWith(".jar")) {
                    jarsByClassLoader
                            .computeIfAbsent(clazz.getClassLoader(), k -> new LinkedHashSet<>())
                            .add(jar);
                }
            } catch (IOException | RuntimeException e) {
                logger.log(Level.FINE, () -> "No jar found for " + clazz + ", it is not preloaded");
            }
        }
        return jarsByClassLoader;
    }
}
