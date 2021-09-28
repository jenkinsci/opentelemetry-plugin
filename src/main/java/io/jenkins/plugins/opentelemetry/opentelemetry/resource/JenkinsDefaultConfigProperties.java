/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.resource;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Copy of <a
 * href="https://github.com/open-telemetry/opentelemetry-java/blob/v1.6.0/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/OpenTelemetrySdkAutoConfiguration.java">
 * io.opentelemetry.sdk.autoconfigure.DefaultConfigProperties</a> because it's a package protected class.
 */
public class JenkinsDefaultConfigProperties implements ConfigProperties {
    private final Map<String, String> config;

    public static ConfigProperties get() {
        return new JenkinsDefaultConfigProperties(System.getProperties(), System.getenv());
    }

    public static ConfigProperties createForTest(Map<String, String> properties) {
        return new JenkinsDefaultConfigProperties(properties, Collections.emptyMap());
    }

    private JenkinsDefaultConfigProperties(Map<?, ?> systemProperties, Map<String, String> environmentVariables) {
        Map<String, String> config = new HashMap();
        environmentVariables.forEach((name, value) -> {
            config.put(name.toLowerCase(Locale.ROOT).replace('_', '.'), value);
        });
        systemProperties.forEach((key, value) -> {
            config.put(((String)key).toLowerCase(Locale.ROOT).replace('-', '.'), (String)value);
        });
        this.config = config;
    }

    @Nullable
    public String getString(String name) {
        return (String)this.config.get(name);
    }

    @Nullable
    public Boolean getBoolean(String name) {
        String value = (String)this.config.get(name);
        return value != null && !value.isEmpty() ? Boolean.parseBoolean(value) : null;
    }

    @Nullable
    public Integer getInt(String name) {
        String value = (String)this.config.get(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException var4) {
                throw newInvalidPropertyException(name, value, "integer");
            }
        } else {
            return null;
        }
    }

    @Nullable
    public Long getLong(String name) {
        String value = (String)this.config.get(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException var4) {
                throw newInvalidPropertyException(name, value, "long");
            }
        } else {
            return null;
        }
    }

    @Nullable
    public Double getDouble(String name) {
        String value = (String)this.config.get(name);
        if (value != null && !value.isEmpty()) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException var4) {
                throw newInvalidPropertyException(name, value, "double");
            }
        } else {
            return null;
        }
    }

    @Nullable
    public Duration getDuration(String name) {
        String value = (String)this.config.get(name);
        if (value != null && !value.isEmpty()) {
            String unitString = getUnitString(value);
            String numberString = value.substring(0, value.length() - unitString.length());

            try {
                long rawNumber = Long.parseLong(numberString.trim());
                TimeUnit unit = getDurationUnit(unitString.trim());
                return Duration.ofMillis(TimeUnit.MILLISECONDS.convert(rawNumber, unit));
            } catch (NumberFormatException var8) {
                throw new ConfigurationException("Invalid duration property " + name + "=" + value + ". Expected number, found: " + numberString);
            } catch (ConfigurationException var9) {
                throw new ConfigurationException("Invalid duration property " + name + "=" + value + ". " + var9.getMessage());
            }
        } else {
            return null;
        }
    }

    public List<String> getList(String name) {
        String value = (String)this.config.get(name);
        return value == null ? Collections.emptyList() : filterBlanksAndNulls(value.split(","));
    }

    public Map<String, String> getMap(String name) {
        return (Map)this.getList(name).stream().map((keyValuePair) -> {
            return filterBlanksAndNulls(keyValuePair.split("=", 2));
        }).map((splitKeyValuePairs) -> {
            if (splitKeyValuePairs.size() != 2) {
                throw new ConfigurationException("Invalid map property: " + name + "=" + (String)this.config.get(name));
            } else {
                return new AbstractMap.SimpleImmutableEntry((String)splitKeyValuePairs.get(0), (String)splitKeyValuePairs.get(1));
            }
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, next) -> {
            return next;
        }, LinkedHashMap::new));
    }

    private static ConfigurationException newInvalidPropertyException(String name, String value, String type) {
        throw new ConfigurationException("Invalid value for property " + name + "=" + value + ". Must be a " + type + ".");
    }

    private static List<String> filterBlanksAndNulls(String[] values) {
        return (List) Arrays.stream(values).map(String::trim).filter((s) -> {
            return !s.isEmpty();
        }).collect(Collectors.toList());
    }

    private static TimeUnit getDurationUnit(String unitString) {
        byte var2 = -1;
        switch(unitString.hashCode()) {
            case 0:
                if (unitString.equals("")) {
                    var2 = 0;
                }
                break;
            case 100:
                if (unitString.equals("d")) {
                    var2 = 5;
                }
                break;
            case 104:
                if (unitString.equals("h")) {
                    var2 = 4;
                }
                break;
            case 109:
                if (unitString.equals("m")) {
                    var2 = 3;
                }
                break;
            case 115:
                if (unitString.equals("s")) {
                    var2 = 2;
                }
                break;
            case 3494:
                if (unitString.equals("ms")) {
                    var2 = 1;
                }
        }

        switch(var2) {
            case 0:
            case 1:
                return TimeUnit.MILLISECONDS;
            case 2:
                return TimeUnit.SECONDS;
            case 3:
                return TimeUnit.MINUTES;
            case 4:
                return TimeUnit.HOURS;
            case 5:
                return TimeUnit.DAYS;
            default:
                throw new ConfigurationException("Invalid duration string, found: " + unitString);
        }
    }

    private static String getUnitString(String rawValue) {
        int lastDigitIndex;
        for(lastDigitIndex = rawValue.length() - 1; lastDigitIndex >= 0; --lastDigitIndex) {
            char c = rawValue.charAt(lastDigitIndex);
            if (Character.isDigit(c)) {
                break;
            }
        }

        return rawValue.substring(lastDigitIndex + 1);
    }
}
