/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import hudson.console.ConsoleNote;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.StringWriter;

/**
 * Utilities for extracting and reinserting the rich formatting of Jenkins log lines (e.g. ANSI color codes).
 * Copied from https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin
 */
public interface LogLineAnnotationExtractor {
    TextAndAnnotations extractAnnotations(byte[] bytes, int len);

    default String recomposeLogLine(String text, JSONArray annotations) {
        if (annotations == null) {
            return text;
        } else {
            StringWriter formattedMessage = new StringWriter();
            int pos = 0;
            for (Object o : annotations) {
                JSONObject annotation = (JSONObject) o;
                int position = annotation.getInt(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS_POSITION_FIELD);
                String note = annotation.getString(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS_NOTE_FIELD);
                formattedMessage.write(text, pos, position - pos);
                formattedMessage.write(ConsoleNote.PREAMBLE_STR);
                formattedMessage.write(note);
                formattedMessage.write(ConsoleNote.POSTAMBLE_STR);
                pos = position;
            }
            formattedMessage.write(text, pos, text.length() - pos);
            return formattedMessage.toString();
        }
    }
}
