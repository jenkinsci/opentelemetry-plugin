/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import com.google.common.collect.ImmutableMap;
import hudson.console.ConsoleNote;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import net.sf.json.JSONArray;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utilities for extracting and reinserting {@link ConsoleNote}s.
 * copied from https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin
 */
public class LogLineAnnotationExtractorImpl implements LogLineAnnotationExtractor, Serializable {
    @Override
    public TextAndAnnotations extractAnnotations(byte[] bytes, int len) {
        assert len > 0 && len <= bytes.length;
        int endOfLine = len;
        while (endOfLine > 0) {
            byte character = bytes[endOfLine - 1];
            if (character == '\n' || character == '\r') {
                endOfLine--;
            } else {
                break;
            }
        }
        String line = new String(bytes, 0, endOfLine, StandardCharsets.UTF_8);
        // Would be more efficient to do searches at the byte[] level, but too much bother for now,
        // especially since there is no standard library method to do offset searches like String has.
        if (!line.contains(ConsoleNote.PREAMBLE_STR)) {
            // Shortcut for the common case that we have no notes.
            return new TextAndAnnotations(line, null);
        } else {
            StringBuilder buf = new StringBuilder();
            List<Map<String, Object>> annotations = new ArrayList<>();
            int pos = 0;
            while (true) {
                int preamble = line.indexOf(ConsoleNote.PREAMBLE_STR, pos);
                if (preamble == -1) {
                    break;
                }
                int endOfPreamble = preamble + ConsoleNote.PREAMBLE_STR.length();
                int postamble = line.indexOf(ConsoleNote.POSTAMBLE_STR, endOfPreamble);
                if (postamble == -1) {
                    // Malformed; stop here.
                    break;
                }
                buf.append(line, pos, preamble);
                annotations.add(
                    ImmutableMap.of(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS_POSITION_FIELD, buf.length(), JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS_NOTE_FIELD, line.substring(endOfPreamble, postamble)));
                pos = postamble + ConsoleNote.POSTAMBLE_STR.length();
            }
            buf.append(line, pos, line.length()); // append tail
            return new TextAndAnnotations(buf.toString(), JSONArray.fromObject(annotations));
        }
    }



}
