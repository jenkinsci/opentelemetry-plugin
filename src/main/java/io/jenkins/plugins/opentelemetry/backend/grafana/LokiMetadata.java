/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;

public interface LokiMetadata {
    String LABEL_SERVICE_NAME = ServiceAttributes.SERVICE_NAME.getKey().replace('.', '_');
    String LABEL_SERVICE_NAMESPACE =
            ServiceIncubatingAttributes.SERVICE_NAMESPACE.getKey().replace('.', '_');
    String META_DATA_TRACE_ID = "trace_id";
    String META_DATA_CI_PIPELINE_ID =
            ExtendedJenkinsAttributes.CI_PIPELINE_ID.getKey().replace('.', '_');
    String META_DATA_CI_PIPELINE_RUN_NUMBER =
            ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER.getKey().replace('.', '_');
    String META_DATA_JENKINS_PIPELINE_STEP_ID =
            ExtendedJenkinsAttributes.JENKINS_STEP_ID.getKey().replace('.', '_');
}
