/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import com.google.common.collect.ImmutableMap;
import hudson.console.ConsoleNote;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.IOException;
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

    public static final AttributeKey<String> MESSAGE_KEY = AttributeKey.stringKey("message");
    public static final AttributeKey<String> ANNOTATIONS_KEY = AttributeKey.stringKey("annotations");
    public static final String POSITION_KEY = "position";
    public static final String NOTE_KEY = "note";

    private ConsoleNotes() {
    }

    public static TextAndAnnotations parse(byte[] bytes, int len) {
        assert len > 0 && len <= bytes.length;
        AttributesBuilder attributes = Attributes.builder();
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
                    ImmutableMap.of(POSITION_KEY, buf.length(), NOTE_KEY, line.substring(endOfPreamble, postamble)));
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

    public static void write(Writer writer, String message, @Nullable JSONArray annotations) throws IOException {
        if (annotations == null) {
            writer.write(message);
        } else {
            int pos = 0;
            for (Object o : annotations) {
                JSONObject annotation = (JSONObject) o;
                int position = annotation.getInt(POSITION_KEY);
                String note = annotation.getString(NOTE_KEY);
                writer.write(message, pos, position - pos);
                writer.write(ConsoleNote.PREAMBLE_STR);
                writer.write(note);
                writer.write(ConsoleNote.POSTAMBLE_STR);
                pos = position;
            }
            writer.write(message, pos, message.length() - pos);
        }
        writer.write('\n');
    }
}
