/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.SpanContext;

public interface ElasticsearchFields {
    /**
     * Field used by the Elastic-Otel mapping to store the {@link io.opentelemetry.api.logs.LogRecordBuilder#setBody(String)}
     */
    String FIELD_MESSAGE = "message";
    /**
     * Mapping for {@link SpanContext#getTraceId()}
     */
    String FIELD_TRACE_ID = "trace.id";
    String FIELD_TIMESTAMP = "@timestamp";
    String FIELD_CI_PIPELINE_ID = "labels." + ExtendedJenkinsAttributes.CI_PIPELINE_ID.getKey().replace('.', '_');
    String FIELD_CI_PIPELINE_RUN_NUMBER = "numeric_labels." + ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER.getKey().replace('.', '_');
    String FIELD_JENKINS_STEP_ID = "labels." + ExtendedJenkinsAttributes.JENKINS_STEP_ID.getKey().replace('.', '_');
    String INDEX_TEMPLATE_PATTERNS = "logs-generic.*,logs-apm.app-*,.ds-logs-apm.app*,.ds-logs-generic.*";
    String INDEX_TEMPLATE_NAME = "logs-generic.*,logs-apm.app";

    /**
     * @see co.elastic.clients.elasticsearch._types.ErrorCause#type()
     */
    String ERROR_CAUSE_TYPE_SECURITY_EXCEPTION = "security_exception";
}
