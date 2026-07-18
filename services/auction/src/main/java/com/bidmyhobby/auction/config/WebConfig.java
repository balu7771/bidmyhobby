package com.bidmyhobby.auction.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // CORS is handled by API Gateway, so no CORS configuration needed here
}