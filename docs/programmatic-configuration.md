# Programmatic Configuration - Jenkins OpenTelemetry Plugin

## Overview

This guide explains how to programmatically configure the OpenTelemetry plugin's resource attributes, particularly `service.name` and `service.namespace`, using the OpenTelemetry SDK's standard extension mechanism.

## Use Cases

Programmatic configuration is useful when:
- You manage multiple Jenkins controllers and want to automatically set unique service names
- You need to configure OpenTelemetry without GUI interaction
- You want to avoid dependencies on the OpenTelemetry plugin's internal APIs
- You're building a plugin that needs to contribute resource attributes

**Example**: CloudBees CI installations with many controllers can automatically set `service.name` to match each controller's name without manual configuration.

## Approach: Custom ResourceProvider

The recommended approach is to implement a custom `io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider`. This is the OpenTelemetry SDK's standard extension point for contributing resource attributes.

### Benefits

- ✅ Uses OpenTelemetry SDK's standard extension mechanism
- ✅ No dependency on OpenTelemetry plugin's internal APIs
- ✅ Works seamlessly with the autoconfiguration framework
- ✅ Can be overridden by explicit GUI configuration when needed
- ✅ Only requires dependency on `opentelemetry-sdk-extension-autoconfigure-spi`

## Implementation Guide

### Step 1: Add Dependency

Add the OpenTelemetry autoconfigure SPI dependency to your plugin's `pom.xml`:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure-spi</artifactId>
    <version>1.44.1</version>
    <scope>provided</scope>
</dependency>
```

**Note**: Use `provided` scope since the OpenTelemetry plugin already includes this dependency.

### Step 2: Implement ResourceProvider

Create a class that implements `ResourceProvider`:

```java
package com.example.jenkins.plugins.otel;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import jenkins.model.Jenkins;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ControllerResourceProvider implements ResourceProvider {
    private static final Logger LOGGER = Logger.getLogger(ControllerResourceProvider.class.getName());

    @Override
    public Resource createResource(ConfigProperties config) {
        ResourceBuilder resourceBuilder = Resource.builder();
        
        try {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null) {
                // Example: Use Jenkins system message as service name
                // In CloudBees CI, this could be the controller name
                String controllerName = jenkins.getSystemMessage();
                if (controllerName != null && !controllerName.isEmpty()) {
                    resourceBuilder.put(ServiceAttributes.SERVICE_NAME, controllerName);
                    LOGGER.log(Level.INFO, "Set service.name to: {0}", controllerName);
                }
                
                // Example: Set service namespace for all controllers
                String namespace = System.getenv("JENKINS_OTEL_NAMESPACE");
                if (namespace != null && !namespace.isEmpty()) {
                    resourceBuilder.put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, namespace);
                    LOGGER.log(Level.INFO, "Set service.namespace to: {0}", namespace);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to configure OpenTelemetry resource", e);
        }
        
        Resource resource = resourceBuilder.build();
        LOGGER.log(Level.FINE, "Controller resource: {0}", resource);
        return resource;
    }
}
```

### Step 3: Register via ServiceLoader

Create a file at `src/main/resources/META-INF/services/io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider` with the fully qualified class name of your implementation:

```
com.example.jenkins.plugins.otel.ControllerResourceProvider
```

### Step 4: Build and Deploy

Build your plugin and deploy it to your Jenkins instance. The ResourceProvider will be automatically discovered and loaded by the OpenTelemetry SDK during initialization.

## Configuration Priority

Resource attributes are merged with the following priority (highest to lowest):

1. **Explicit GUI Configuration**: Values set in "Manage Jenkins / Configure System / OpenTelemetry"
2. **System Properties**: Values from `-Dotel.service.name=...`
3. **Environment Variables**: Values from `OTEL_SERVICE_NAME=...`
4. **Custom ResourceProviders**: Values from your custom implementation
5. **Default ResourceProviders**: Default values (e.g., "jenkins")

This means your custom ResourceProvider provides defaults that can be overridden by administrators when needed.

## Real-World Example: CloudBees CI

For CloudBees CI, you might want to:

```java
public class CloudBeesControllerResourceProvider implements ResourceProvider {
    @Override
    public Resource createResource(ConfigProperties config) {
        ResourceBuilder resourceBuilder = Resource.builder();
        
        // Get controller name from CloudBees-specific API or configuration
        String controllerName = getCloudBeesControllerName();
        if (controllerName != null) {
            resourceBuilder.put(ServiceAttributes.SERVICE_NAME, controllerName);
        }
        
        // Set a common namespace for all controllers in this installation
        resourceBuilder.put(ServiceIncubatingAttributes.SERVICE_NAMESPACE, "cloudbees-ci");
        
        return resourceBuilder.build();
    }
    
    private String getCloudBeesControllerName() {
        // Implementation depends on CloudBees CI APIs
        // This is just a placeholder
        return System.getProperty("cloudbees.controller.name", 
                                  System.getenv("CONTROLLER_NAME"));
    }
}
```

## Using ConfigProperties

The `ConfigProperties` parameter allows you to read configuration from system properties and environment variables:

```java
@Override
public Resource createResource(ConfigProperties config) {
    ResourceBuilder resourceBuilder = Resource.builder();
    
    // Read custom configuration property
    String serviceName = config.getString("jenkins.otel.service.name");
    if (serviceName != null) {
        resourceBuilder.put(ServiceAttributes.SERVICE_NAME, serviceName);
    }
    
    return resourceBuilder.build();
}
```

Users can then configure via system properties:
```
-Djenkins.otel.service.name=my-controller
```

Or environment variables:
```
export JENKINS_OTEL_SERVICE_NAME=my-controller
```

## Testing

To test your ResourceProvider implementation:

1. **Unit Test**: Test the `createResource()` method in isolation with mock `ConfigProperties`
2. **Integration Test**: Deploy to a test Jenkins instance and verify attributes appear in telemetry data
3. **Override Test**: Verify GUI configuration properly overrides your ResourceProvider values

Example unit test:

```java
@Test
public void testResourceProvider() {
    Map<String, String> props = new HashMap<>();
    ConfigProperties config = DefaultConfigProperties.createFromMap(props);
    
    ControllerResourceProvider provider = new ControllerResourceProvider();
    Resource resource = provider.createResource(config);
    
    // Verify expected attributes
    assertNotNull(resource.getAttribute(ServiceAttributes.SERVICE_NAME));
}
```

## Troubleshooting

### ResourceProvider Not Loaded

If your ResourceProvider isn't being loaded:

1. Verify the `META-INF/services` file path is correct
2. Check the file contains the correct fully qualified class name
3. Ensure there are no typos in the class name
4. Check Jenkins logs for any errors during plugin initialization

### Values Not Appearing

If your values aren't appearing in telemetry:

1. Check if GUI configuration is overriding your values (this is expected behavior)
2. Verify your ResourceProvider is returning a non-empty Resource
3. Enable debug logging: `-Djava.util.logging.config.file=logging.properties` with appropriate log levels
4. Check the OpenTelemetry plugin logs for resource configuration details

## API Stability

The following APIs are considered **stable and public**:

- `io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider` (OpenTelemetry SDK interface)
- `io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties` (OpenTelemetry SDK interface)
- `io.opentelemetry.sdk.resources.Resource` (OpenTelemetry SDK class)
- `io.opentelemetry.semconv.ServiceAttributes` (OpenTelemetry semantic conventions)

These are part of the OpenTelemetry SDK's public API and follow semantic versioning.

**Not recommended**: Calling `JenkinsOpenTelemetryPluginConfiguration.setServiceName()` or `configureOpenTelemetrySdk()` directly, as these are internal plugin APIs subject to change.

## Additional Resources

- [OpenTelemetry SDK Autoconfiguration](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure)
- [OpenTelemetry Resource Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/resource/)
- [Example ResourceProvider Implementation](https://github.com/jenkinsci/opentelemetry-api-plugin/blob/main/src/main/java/io/jenkins/plugins/opentelemetry/api/instrumentation/resource/JenkinsResourceProvider.java)

## Related Issues

- [Issue #1155: Ability to configure service.namespace and service.name programmatically](https://github.com/jenkinsci/opentelemetry-plugin/issues/1155)
