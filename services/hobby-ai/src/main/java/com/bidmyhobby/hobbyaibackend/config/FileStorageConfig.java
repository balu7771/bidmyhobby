package com.bidmyhobby.hobbyaibackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class FileStorageConfig implements WebMvcConfigurer {

    @Value("${app.storage.images-dir:images}")
    private String imagesDir;
    
    @Bean
    public boolean createDirectories() {
        // Create images directory if it doesn't exist
        File imagesDirFile = new File(imagesDir);
        if (!imagesDirFile.exists()) {
            imagesDirFile.mkdirs();
        }
        
        // Create data directory for H2 database if it doesn't exist
        File dataDirFile = new File("data");
        if (!dataDirFile.exists()) {
            dataDirFile.mkdirs();
        }
        
        return true;
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Map /images/** to the images directory
        Path imagesPath = Paths.get(imagesDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + imagesPath.toString() + "/");
    }
}