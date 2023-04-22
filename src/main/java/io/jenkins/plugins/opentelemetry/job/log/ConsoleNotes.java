/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.common.collect.ImmutableMap;
import hudson.console.ConsoleNote;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utilities for extracting and reinserting {@link ConsoleNote}s.
 * copied from https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin
 */
public class ConsoleNotes {

    private ConsoleNotes() {
    }

    public static TextAndAnnotations parse(byte[] bytes, int len) {
        assert len > 0 && len <= bytes.length;
        int eol = len;
        while (eol > 0) {
            byte c = bytes[eol - 1];
            if (c == '\n' || c == '\r') {
                eol--;
            } else {
                break;
            }
        }
        String line = new String(bytes, 0, eol, StandardCharsets.UTF_8);
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

    static class TextAndAnnotations {
        final String text;
        @CheckForNull
        final JSONArray annotations;

        public TextAndAnnotations(String text, @Nullable JSONArray annotations) {
            this.text = text;
            this.annotations = annotations;
        }
    }

    public static String readFormattedMessage(String message, @Nullable JSONArray annotations) {
        if (annotations == null) {
            return message;
        } else {
            StringWriter formattedMessage = new StringWriter();
            int pos = 0;
            for (Object o : annotations) {
                JSONObject annotation = (JSONObject) o;
                int position = annotation.getInt(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS_POSITION_FIELD);
                String note = annotation.getString(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS_NOTE_FIELD);
                formattedMessage.write(message, pos, position - pos);
                formattedMessage.write(ConsoleNote.PREAMBLE_STR);
                formattedMessage.write(note);
                formattedMessage.write(ConsoleNote.POSTAMBLE_STR);
                pos = position;
            }
            formattedMessage.write(message, pos, message.length() - pos);
            return formattedMessage.toString();
        }
    }
}
