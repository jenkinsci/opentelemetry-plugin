/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.OpenTelemetryAttributesAction;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import java.util.Map;

/**
 * Resolves the {@link ExtendedJenkinsAttributes#MZAKRZE_TENANT_ID} attribute value for a build,
 * from the build's root Jenkins folder (e.g. "org-aaa").
 */
public final class TenantIdResolver {

    private static final String UNKNOWN_TENANT_ID = "unknown";

    private static final Map<String, String> ROOT_FOLDER_TO_TENANT_ID = Map.of(
            "org-aaa", "aragorn",
            "org-bbb", "boromir",
            "org-ccc", "celeborn",
            "org-ddd", "denethor",
            "org-eee", "eowyn",
            "org-fff", "frodo");

    private TenantIdResolver() {}

    /**
     * @param jobFullName see {@link hudson.model.Job#getFullName()}
     * @return the tenant id for the job's root folder, {@value #UNKNOWN_TENANT_ID} if unmapped
     */
    public static String resolveFromJobFullName(String jobFullName) {
        int slashIndex = jobFullName.indexOf('/');
        String rootFolder = slashIndex == -1 ? jobFullName : jobFullName.substring(0, slashIndex);
        return ROOT_FOLDER_TO_TENANT_ID.getOrDefault(rootFolder, UNKNOWN_TENANT_ID);
    }

    /**
     * Resolves the tenant id for this run, computing it once and caching it in the run's
     * {@link OpenTelemetryAttributesAction} so every subsequent call for the same run reuses the same value.
     */
    public static String resolve(Run<?, ?> run) {
        OpenTelemetryAttributesAction action = run.getAction(OpenTelemetryAttributesAction.class);
        if (action == null) {
            action = new OpenTelemetryAttributesAction();
            run.addAction(action);
        }
        return (String) action.getAttributes()
                .computeIfAbsent(
                        ExtendedJenkinsAttributes.MZAKRZE_TENANT_ID,
                        key -> resolveFromJobFullName(run.getParent().getFullName()));
    }
}
