/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ElasticLogsBackend implements Describable<ElasticLogsBackend>, ExtensionPoint {
    private final static Logger logger = Logger.getLogger(ElasticLogsBackend.class.getName());

    private transient Template buildLogsVisualizationUrlGTemplate;

    public Descriptor<ElasticLogsBackend> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    /**
     * Returns {@code null} if the backend is not capable of retrieving logs(ie the {@link NoElasticLogsBackend}
     */
    @CheckForNull
    public abstract LogStorageRetriever getLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider);

    /**
     * Returns all the registered {@link ElasticLogsBackend} descriptors.
     */
    public static DescriptorExtensionList<ElasticLogsBackend, Descriptor<ElasticLogsBackend>> all() {
        return Jenkins.get().getDescriptorList(ElasticLogsBackend.class);
    }

    public Template getBuildLogsVisualizationUrlTemplate() {
        // see https://www.elastic.co/guide/en/kibana/6.8/sharing-dashboards.html

        if (this.buildLogsVisualizationUrlGTemplate == null) {
            try {
                String kibanaSpaceBaseUrl;
                if (StringUtils.isBlank(this.getKibanaSpaceIdentifier())) {
                    kibanaSpaceBaseUrl = "${kibanaBaseUrl}";
                } else {
                    kibanaSpaceBaseUrl = "${kibanaBaseUrl}/s/" + URLEncoder.encode(this.getKibanaSpaceIdentifier(), StandardCharsets.UTF_8.name());
                }

                String urlTemplate = kibanaSpaceBaseUrl + "/app/logs/stream?" +
                    "logPosition=(end:now,start:now-1d,streamLive:!f)&" +
                    "logFilter=(language:kuery,query:%27trace.id:${traceId}%27)&";
                GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
                try {
                    this.buildLogsVisualizationUrlGTemplate = gStringTemplateEngine.createTemplate(urlTemplate);
                } catch (IOException | ClassNotFoundException e) {
                    logger.log(Level.WARNING, "Invalid build logs Visualisation URL Template '" + urlTemplate + "'", e);
                    this.buildLogsVisualizationUrlGTemplate = ObservabilityBackend.ERROR_TEMPLATE;
                }
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return buildLogsVisualizationUrlGTemplate;
    }

    private String getKibanaSpaceIdentifier() {
        // FIXME
        return "";
    }
}
