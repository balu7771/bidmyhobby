package com.bidmyhobby.hobbyaibackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.images-dir:images}")
    private String imagesDir;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve images from the file system
        Path imagesPath = Paths.get(imagesDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + imagesPath.toString() + "/");
                
        // Serve H2 console
        registry.addResourceHandler("/h2-console/**")
                .addResourceLocations("classpath:/h2-console/");
    }
}