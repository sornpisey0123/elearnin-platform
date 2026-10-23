package com.pisey.elearning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * {@code app.storage.*} properties: local directory for uploaded files.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(Path root) {
}
