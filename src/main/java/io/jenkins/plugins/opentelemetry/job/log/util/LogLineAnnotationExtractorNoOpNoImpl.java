/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import java.io.Serializable;

public class LogLineAnnotationExtractorNoOpNoImpl implements LogLineAnnotationExtractor, Serializable {
    @Override
    public TextAndAnnotations extractAnnotations(byte[] bytes, int len) {
        return new TextAndAnnotations(new String(bytes, 0, len), null);
    }
}
