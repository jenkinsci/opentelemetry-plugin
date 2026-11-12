/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import hudson.Plugin;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.Run;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.libs.LibrariesAction;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Pipeline Groovy Libraries is an optional dependency: its classes must only be touched when the plugin is installed
 * and active.
 */
public class PipelineLibrariesTest {

    private static final String PLUGIN_NAME = "pipeline-groovy-lib";

    private static Jenkins jenkinsWithPlugin(PluginWrapper plugin) {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin(PLUGIN_NAME)).thenReturn(plugin);
        Jenkins jenkins = mock(Jenkins.class);
        when(jenkins.getPluginManager()).thenReturn(pluginManager);
        return jenkins;
    }

    @Test
    public void testNoLibrariesAndTheRunIsNotReadWhenThePluginIsNotInstalled() {
        Run<?, ?> run = mock(Run.class);
        Jenkins instance = jenkinsWithPlugin(null);
        try (MockedStatic<Jenkins> jenkins = mockStatic(Jenkins.class)) {
            jenkins.when(Jenkins::get).thenReturn(instance);

            assertEquals(List.of(), PipelineLibraries.namesAndVersions(run));
        }
        verifyNoInteractions(run);
    }

    @Test
    public void testNoLibrariesAndTheRunIsNotReadWhenThePluginIsDisabled() {
        Run<?, ?> run = mock(Run.class);
        PluginWrapper disabled = mock(PluginWrapper.class);
        when(disabled.isActive()).thenReturn(false);
        Jenkins instance = jenkinsWithPlugin(disabled);
        // Jenkins#getPlugin(String) can return the instance of a plugin that is loaded but not active
        when(instance.getPlugin(PLUGIN_NAME)).thenReturn(mock(Plugin.class));
        try (MockedStatic<Jenkins> jenkins = mockStatic(Jenkins.class)) {
            jenkins.when(Jenkins::get).thenReturn(instance);

            assertEquals(List.of(), PipelineLibraries.namesAndVersions(run));
        }
        verifyNoInteractions(run);
    }

    @Test
    public void testTheRunIsReadWhenThePluginIsActive() {
        Run<?, ?> run = mock(Run.class);
        when(run.getAction(any(Class.class))).thenReturn(null);
        PluginWrapper active = mock(PluginWrapper.class);
        when(active.isActive()).thenReturn(true);
        Jenkins instance = jenkinsWithPlugin(active);
        try (MockedStatic<Jenkins> jenkins = mockStatic(Jenkins.class)) {
            jenkins.when(Jenkins::get).thenReturn(instance);

            assertEquals(List.of(), PipelineLibraries.namesAndVersions(run));
        }
        verify(run).getAction(LibrariesAction.class);
    }
}
