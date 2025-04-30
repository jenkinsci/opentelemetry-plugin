/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import com.google.errorprone.annotations.MustBeClosed;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.GrafanaBackend;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import jenkins.model.Jenkins;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class GrafanaLogsBackend extends AbstractDescribableImpl<GrafanaLogsBackend> implements ExtensionPoint {
    public enum LokiOTelLogFormat {
        LOKI_V2_JSON_OTEL_FORMAT ("Loki V2 OTel logs format as JSON"),
        LOKI_V3_OTEL_FORMAT("Loki V3 OTel logs format using Loki labels and structured metadata");

        final String displayName;

        LokiOTelLogFormat(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final static Logger logger = Logger.getLogger(GrafanaLogsBackend.class.getName());

    private transient Template buildLogsVisualizationUrlGTemplate;

    protected LokiOTelLogFormat lokiOTelLogFormat;

    /**
     * Returns {@code null} if the backend is not capable of retrieving logs(ie the {@link NoGrafanaLogsBackend}
     */
    @CheckForNull
    @MustBeClosed
    public abstract LogStorageRetriever newLogStorageRetriever(TemplateBindingsProvider templateBindingsProvider);

    @NonNull
    public Template getBuildLogsVisualizationMessageTemplate() {
        try {
            return new GStringTemplateEngine().createTemplate("View build logs in ${backendName}");
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @NonNull
    public Template getBuildLogsVisualizationUrlTemplate() {
        if (this.buildLogsVisualizationUrlGTemplate == null) {
            final String START = "__START__";
            final String END = "__END__";

            String logQlQuery;
            switch (Optional.ofNullable(lokiOTelLogFormat).orElse(LokiOTelLogFormat.LOKI_V2_JSON_OTEL_FORMAT)) {
                case LOKI_V3_OTEL_FORMAT:
                    // skip filtering on the optional `service.namespace` as the logic would be complex when it's not required to retrieve the right log lines
                    logQlQuery = "{service_name=\"" + START + GrafanaBackend.TemplateBindings.SERVICE_NAME + END + "\"} " +
                        "| trace_id=\"" + START + GrafanaBackend.TemplateBindings.TRACE_ID + END + "\"";
                    break;
                case LOKI_V2_JSON_OTEL_FORMAT:
                default:
                    logQlQuery =
                        "{job=\"" + START + GrafanaBackend.TemplateBindings.SERVICE_NAMESPACE_AND_NAME + END + "\"} " +
                            "| json " +
                            "| traceid=\"" + START + GrafanaBackend.TemplateBindings.TRACE_ID + END + "\" " +
                            "| line_format \"{{.body}}\"";
            }
            JsonObject panesAsJson = Json.createObjectBuilder()
                .add("NZj", Json.createObjectBuilder()
                    .add("datasource", START + GrafanaBackend.TemplateBindings.GRAFANA_LOKI_DATASOURCE_IDENTIFIER + END)
                    .add("queries", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("refId", "A")
                        .add("expr", logQlQuery)
                        .add("queryType", "range")
                        .add("datasource", Json.createObjectBuilder()
                            .add("type", "loki")
                            .add("uid", START + GrafanaBackend.TemplateBindings.GRAFANA_LOKI_DATASOURCE_IDENTIFIER + END)
                        )
                        .add("editorMode", "code")))
                    .add("range", Json.createObjectBuilder()
                        .add("from", "--start_time--")
                        .add("to", "--end_time--")))
                .build();

            StringWriter panesAsStringWriter = new StringWriter();
            Json.createWriter(panesAsStringWriter).writeObject(panesAsJson);

            // starttime and endtime are of type java.time.Instant
            String panes = URLEncoder
                .encode(panesAsStringWriter.toString(), StandardCharsets.UTF_8)
                .replace(START, "${").replace(END, "}")
                .replace("--start_time--", "${" + GrafanaBackend.TemplateBindings.START_TIME + ".minus(1, java.time.temporal.ChronoUnit.DAYS).atZone(java.util.TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli()" + "}")
                .replace("--end_time--", "${" + GrafanaBackend.TemplateBindings.END_TIME + ".plus(1, java.time.temporal.ChronoUnit.DAYS).atZone(java.util.TimeZone.getDefault().toZoneId()).toInstant().toEpochMilli()" + "}");


            String urlTemplate = "${" + GrafanaBackend.TemplateBindings.GRAFANA_BASE_URL + "}/" +
                "explore?" +
                "panes=" + panes +
                "&schemaVersion=1" +
                "&orgId=${" + GrafanaBackend.TemplateBindings.GRAFANA_ORG_ID + "}";

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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @NonNull
    public String getLokiOTelLogFormat() {
        return Optional.ofNullable(lokiOTelLogFormat).map(Enum::name).orElse(getDescriptor().getDefaultLokiOTelLogFormat());
    }

    @DataBoundSetter
    public void setLokiOTelLogFormat(String lokiOTelLogFormat) {
        this.lokiOTelLogFormat = LokiOTelLogFormat.valueOf(lokiOTelLogFormat);
    }

    /**
     * Returns all the registered {@link GrafanaLogsBackend} descriptors.
     */
    public static DescriptorExtensionList<GrafanaLogsBackend, Descriptor<GrafanaLogsBackend>> all() {
        return Jenkins.get().getDescriptorList(GrafanaLogsBackend.class);
    }

    public abstract static class DescriptorImpl extends Descriptor<GrafanaLogsBackend> {
        public ListBoxModel doFillLokiOTelLogFormatItems() {
            ListBoxModel items = new ListBoxModel();
            for (LokiOTelLogFormat lokiOTelLogFormat : LokiOTelLogFormat.values()) {
                items.add(lokiOTelLogFormat.getDisplayName(), lokiOTelLogFormat.name());
            }
            return items;
        }

        public abstract String getDefaultLokiOTelLogFormat();
    }
}
