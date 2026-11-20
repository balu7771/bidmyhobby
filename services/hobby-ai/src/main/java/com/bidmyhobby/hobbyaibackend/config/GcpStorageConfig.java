package com.bidmyhobby.hobbyaibackend.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcpStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(GcpStorageConfig.class);

    @Value("${gcp.project-id:${GCP_PROJECT_ID:}}")
    private String projectId;

    @Bean
    public Storage googleCloudStorage() {
        try {
            StorageOptions.Builder builder = StorageOptions.newBuilder();
            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId.trim());
            }
            Storage storage = builder.build().getService();
            log.info("Initialized Google Cloud Storage client for hobby-ai service");
            return storage;
        } catch (Exception e) {
            log.warn("Google Cloud Storage client initialization fallback for hobby-ai: {}", e.getMessage());
            return null;
        }
    }
}
