/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.google.errorprone.annotations.MustBeClosed;
import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ElasticLogsBackend extends AbstractDescribableImpl<ElasticLogsBackend> implements Describable<ElasticLogsBackend>, ExtensionPoint {
    private final static Logger logger = Logger.getLogger(ElasticLogsBackend.class.getName());

    private transient Template buildLogsVisualizationUrlGTemplate;

    /**
     * Returns {@code null} if the backend is not capable of retrieving logs(ie the {@link NoElasticLogsBackend}
     */
    @CheckForNull
    @MustBeClosed
    public abstract LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider);

    public Template getBuildLogsVisualizationMessageTemplate() {
        try {
            return new GStringTemplateEngine().createTemplate("View build logs in ${backendName}");
        } catch (ClassNotFoundException|IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public Template getBuildLogsVisualizationUrlTemplate() {
        // see https://www.elastic.co/guide/en/kibana/6.8/sharing-dashboards.html

        if (this.buildLogsVisualizationUrlGTemplate == null) {
            String kibanaSpaceBaseUrl;
            if (StringUtils.isBlank(this.getKibanaSpaceIdentifier())) {
                kibanaSpaceBaseUrl = "${kibanaBaseUrl}";
            } else {
                kibanaSpaceBaseUrl = "${kibanaBaseUrl}/s/" + URLEncoder.encode(this.getKibanaSpaceIdentifier(), StandardCharsets.UTF_8);
            }

            String urlTemplate = kibanaSpaceBaseUrl + "/app/logs/stream?" +
                "logPosition=(end:now,start:now-40d,streamLive:!f)&" +
                "logFilter=(language:kuery,query:%27trace.id:${traceId}%27)&";
            GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
            try {
                this.buildLogsVisualizationUrlGTemplate = gStringTemplateEngine.createTemplate(urlTemplate);
            } catch (IOException | ClassNotFoundException e) {
                logger.log(Level.WARNING, "Invalid build logs Visualisation URL Template '" + urlTemplate + "'", e);
                this.buildLogsVisualizationUrlGTemplate = ObservabilityBackend.ERROR_TEMPLATE;
            }
        }
        return buildLogsVisualizationUrlGTemplate;
    }

    public Map<String, String> getOtelConfigurationProperties() {
        return Collections.singletonMap("otel.logs.exporter", "otlp");
    }

    private String getKibanaSpaceIdentifier() {
        // FIXME implement getKibanaSpaceIdentifier
        return "";
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Returns all the registered {@link ElasticLogsBackend} descriptors.
     */
    public static DescriptorExtensionList<ElasticLogsBackend, Descriptor<ElasticLogsBackend>> all() {
        return Jenkins.get().getDescriptorList(ElasticLogsBackend.class);
    }

    public abstract static class DescriptorImpl extends Descriptor<ElasticLogsBackend> {

    }
}
