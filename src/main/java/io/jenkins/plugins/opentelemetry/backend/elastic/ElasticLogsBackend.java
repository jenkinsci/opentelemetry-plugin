/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import com.google.errorprone.annotations.MustBeClosed;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import jenkins.model.Jenkins;

public abstract class ElasticLogsBackend extends AbstractDescribableImpl<ElasticLogsBackend> implements ExtensionPoint {
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
            String spaceIdentifier = this.getKibanaSpaceIdentifier();
            if (StringUtils.isBlank(spaceIdentifier)) {
                kibanaSpaceBaseUrl = "${kibanaBaseUrl}";
            } else {
                kibanaSpaceBaseUrl = "${kibanaBaseUrl}/s/${spaceIdentifier}";
            }
            String urlTemplate = kibanaSpaceBaseUrl + "/app/discover#/" +
                "?_a=(" +
                "columns:!(message)," +
                "dataSource:(dataViewId:discover-observability-solution-all-logs,type:dataView)," +
                "filters:!((" +
                    "meta:(alias:!n,disabled:!f,field:trace.id,index:discover-observability-solution-all-logs,key:trace.id,negate:!f,params:(query:%27${traceId}%27),type:phrase)," +
                    "query:(match_phrase:(trace.id:%27${traceId}%27))" +
                    ")))" +
                "&_g=(filters:!(),time:(from:now-40d,to:now))";
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
        String ret = "";
        Optional<ElasticBackend> backend = ElasticBackend.get();
        if (!backend.isEmpty()) {
            ElasticBackend elasticLogsBackend = backend.get();
            ret = elasticLogsBackend.getKibanaSpaceIdentifier();
        }
        return ret;
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
