/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanContext;

public interface ElasticsearchFields {
    /**
     * Field used by the Elastic-Otel mapping to store the {@link io.opentelemetry.sdk.logs.LogBuilder#setBody(String)}
     */
    String FIELD_MESSAGE = "message";
    /**
     * Mapping for {@link SpanContext#getTraceId()}
     */
    String FIELD_TRACE_ID = "trace.id";
    String FIELD_TIMESTAMP = "@timestamp";
    String FIELD_CI_PIPELINE_ID = "labels." + JenkinsOtelSemanticAttributes.CI_PIPELINE_ID.getKey().replace('.', '_');
    String FIELD_CI_PIPELINE_RUN_NUMBER = "numeric_labels." + JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER.getKey().replace('.', '_');
    String FIELD_JENKINS_STEP_ID = "labels." + JenkinsOtelSemanticAttributes.JENKINS_STEP_ID.getKey().replace('.', '_');
    String INDEX_TEMPLATE_PATTERNS = "logs-apm.app-*";
    String INDEX_TEMPLATE_NAME = "logs-apm.app";
    /**
     * Waiting to fix https://github.com/jenkinsci/opentelemetry-plugin/issues/336 , we hard code the policy name
     */
    String INDEX_LIFECYCLE_POLICY_NAME = "logs-apm.app_logs-default_policy";


    /**
     * @see co.elastic.clients.elasticsearch._types.ErrorCause#type()
     */
    String ERROR_CAUSE_TYPE_SECURITY_EXCEPTION = "security_exception";
}
