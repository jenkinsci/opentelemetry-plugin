/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import io.opentelemetry.api.trace.SpanContext;

public interface ElasticsearchFields {
    /**
     * Field used by the Elastic-Otel mapping to store the {@link io.opentelemetry.api.logs.LogRecordBuilder#setBody(String)}
     */
    String FIELD_MESSAGE = "body";
    /**
     * Mapping for {@link SpanContext#getTraceId()}
     */
    String FIELD_TRACE_ID = "trace.id";

    String FIELD_TIMESTAMP = "@timestamp";
    String INDEX_TEMPLATE_PATTERNS = "logs-*";

    /**
     * Legacy APM field names
     */
    String LEGACY_FIELD_TRACE_ID = "trace.id";

    String LEGACY_FIELD_CI_PIPELINE_ID = "labels.ci_pipeline_id";
    String LEGACY_FIELD_CI_PIPELINE_RUN_NUMBER = "numeric_labels.ci_pipeline_run_number";
    String LEGACY_FIELD_JENKINS_STEP_ID = "labels.jenkins_pipeline_step_id";

    /**
     * @see co.elastic.clients.elasticsearch._types.ErrorCause#type()
     */
    String ERROR_CAUSE_TYPE_SECURITY_EXCEPTION = "security_exception";
}
