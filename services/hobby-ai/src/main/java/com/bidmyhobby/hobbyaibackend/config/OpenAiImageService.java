package com.bidmyhobby.hobbyaibackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@ConditionalOnProperty(name = "openai.image.service.enabled", havingValue = "true", matchIfMissing = false)
public class OpenAiImageService {

    private final String imagesDir;

    public OpenAiImageService(
            @Value("${app.storage.images-dir:images}") String imagesDir) {
        this.imagesDir = imagesDir;
    }

    /**
     * Placeholder for image generation - AWS Bedrock doesn't support image generation yet
     * This service is disabled by default
     */
    public boolean generateAndSaveImage(String prompt, String fileName) {
        System.out.println("Image generation is disabled. Using default images.");
        return false;
    }
}