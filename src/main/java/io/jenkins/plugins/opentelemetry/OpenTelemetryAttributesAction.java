/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import hudson.PluginWrapper;
import hudson.model.InvisibleAction;
import io.jenkins.plugins.opentelemetry.job.MonitoringAction;
import io.opentelemetry.api.common.AttributeKey;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @see io.opentelemetry.api.common.AttributeKey
 * @see io.opentelemetry.api.common.AttributeType
 */
public class OpenTelemetryAttributesAction extends InvisibleAction {
    private final static Logger LOGGER = Logger.getLogger(MonitoringAction.class.getName());

    private transient Map<AttributeKey<?>, Object> attributes;
    private ConcurrentMap<String, StepPlugin> loadedStepsPlugins = new ConcurrentHashMap<>();

    @Nonnull
    public Map<AttributeKey<?>, Object> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    @Nonnull
    public ConcurrentMap<String, StepPlugin> getLoadedStepsPlugins() {
        return loadedStepsPlugins;
    }

    public void addStepPlugin(String stepName, StepPlugin c) {
        loadedStepsPlugins.put(stepName, c);
    }

    @Nonnull
    public StepPlugin findStepPlugin(String stepName, Class c) {
        StepPlugin data = loadedStepsPlugins.get(stepName);
        if (data!=null) {
            LOGGER.log(Level.FINEST, " found the plugin for the step '" + stepName + "' - " + data);
            return data;
        }

        data = new StepPlugin();
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins!=null) {
            PluginWrapper wrapper = jenkins.getPluginManager().whichPlugin(c);
            if (wrapper!=null) {
                data = new StepPlugin(wrapper.getShortName(), wrapper.getVersion());
                addStepPlugin(stepName, data);
            }
        }
        return data;
    }

    @Override
    public String toString() {
        return "OpenTelemetryAttributesAction{" +
                "attributes=" + getAttributes().entrySet().stream().map(e -> e.getKey().getKey() + "-" + e.getKey().getType() + " - " + e.getValue()).collect(Collectors.joining(", ")) +
                "loadedSteps=" + getLoadedStepsPlugins().entrySet().stream().map(e -> e.getKey() + "-" + e.getValue()).collect(Collectors.joining(", ")) +
                '}';
    }

    @Immutable
    public static class StepPlugin {
        final String name;
        final String version;

        public StepPlugin(String name, String version) {
            this.name = name;
            this.version = version;
        }

        public StepPlugin() {
            this.name = "unknown";
            this.version = "unknown";
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return "StepPlugin{" +
                "name=" + name +
                ", version=" + version +
            '}';
        }
    }
}
