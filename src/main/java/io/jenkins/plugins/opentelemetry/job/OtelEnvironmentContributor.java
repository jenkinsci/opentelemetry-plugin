/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.logging.Logger;

@Extension
public class OtelEnvironmentContributor extends EnvironmentContributor {
    private final static Logger LOGGER = Logger.getLogger(OtelEnvironmentContributor.class.getName());

    private OtelEnvironmentContributorService otelEnvironmentContributorService;

    private OtelTraceService otelTraceService;

    @Override
    public void buildEnvironmentFor(@Nonnull Run run, @Nonnull EnvVars envs, @Nonnull TaskListener listener) {
        otelEnvironmentContributorService.addEnvironmentVariables(run, envs, otelTraceService.getSpan(run, false));
    }

    @Inject
    public void setOtelTraceService(OtelTraceService otelTraceService) {
        this.otelTraceService = otelTraceService;
    }

    @Inject
    public void setEnvironmentContributorService(OtelEnvironmentContributorService otelEnvironmentContributorService) {
        this.otelEnvironmentContributorService = otelEnvironmentContributorService;
    }
}
