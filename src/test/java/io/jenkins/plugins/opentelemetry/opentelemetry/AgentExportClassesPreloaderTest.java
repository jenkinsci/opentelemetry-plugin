/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.remoting.Which;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AgentExportClassesPreloaderTest {

    @Test
    void the_jars_of_the_export_path_are_grouped_by_class_loader() throws Exception {
        Map<ClassLoader, Set<File>> jarsByClassLoader = AgentExportClassesPreloader.jarsByClassLoader();

        Set<String> jarNames = jarsByClassLoader.values().stream()
                .flatMap(Set::stream)
                .map(File::getName)
                .collect(Collectors.toSet());
        for (String jarPrefix : new String[] {
            "opentelemetry-api-",
            "opentelemetry-context-",
            "opentelemetry-common-",
            "opentelemetry-sdk-1",
            "opentelemetry-sdk-common-",
            "opentelemetry-sdk-logs-",
            "opentelemetry-exporter-common-",
            "opentelemetry-exporter-otlp-common-",
            "opentelemetry-exporter-otlp-1",
            "opentelemetry-exporter-sender-okhttp-",
            "okhttp",
            "okio",
            "kotlin-stdlib-"
        }) {
            assertTrue(
                    jarNames.stream().anyMatch(name -> name.startsWith(jarPrefix)),
                    jarPrefix + "* is preloaded: " + jarNames);
        }
        assertTrue(jarNames.stream().allMatch(name -> name.endsWith(".jar")), "only jars: " + jarNames);
        assertTrue(jarsByClassLoader
                .get(SdkLoggerProvider.class.getClassLoader())
                .contains(Which.jarFile(OtlpHttpLogRecordExporter.class)));
        assertEquals(
                jarNames.size(),
                jarsByClassLoader.values().stream().mapToInt(Set::size).sum(),
                "each jar is preloaded once");
    }
}
