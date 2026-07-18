package com.bidmyhobby.hobbyaibackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class H2ConsoleConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Add a redirect from /db to the H2 console
        registry.addRedirectViewController("/db", "/h2-console");
    }
}