/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.PluginWrapper;
import hudson.model.Run;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.libs.LibrariesAction;

/**
 * Reads the libraries that the Pipeline Groovy Libraries plugin recorded on a run. That plugin is optional: the
 * classes of it are only touched when it is installed and active.
 */
final class PipelineLibraries {

    private static final String PLUGIN_NAME = "pipeline-groovy-lib";

    private PipelineLibraries() {}

    /**
     * @return the libraries of the run as {@code name@version}, empty when the plugin is not installed or is
     *     disabled, or when the run has no libraries
     */
    @NonNull
    static List<String> namesAndVersions(@NonNull Run<?, ?> run) {
        // Not Jenkins#getPlugin(String): it can return the instance of a plugin that is loaded but not active, and
        // the classes of such a plugin cannot be loaded (see the Javadoc of that method). The core refuses to load
        // this plugin if an active pipeline-groovy-lib is older than the version it declares, so there is no
        // LinkageError to handle for an old version, only for a plugin that is not active.
        PluginWrapper plugin = Jenkins.get().getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isActive()) {
            return List.of();
        }
        return Reader.read(run);
    }

    /** Holds the references to the optional plugin, so that they are only resolved when it is active. */
    private static final class Reader {
        static List<String> read(Run<?, ?> run) {
            LibrariesAction action = run.getAction(LibrariesAction.class);
            if (action == null) {
                return List.of();
            }
            return action.getLibraries().stream()
                    .map(library -> library.getName() + "@" + library.getVersion())
                    .toList();
        }
    }
}
