package com.bidmyhobby.auction.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class AwsConfig {

    private static final Logger log = LoggerFactory.getLogger(AwsConfig.class);

    @Value("${aws.accessKeyId:}")
    private String accessKeyId;

    @Value("${aws.secretKey:}")
    private String secretKey;

    @Value("${aws.sessionToken:}")
    private String sessionToken;

    @Value("${aws.region:ap-south-1}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    private AwsCredentialsProvider credentialsProvider() {
        String key = accessKeyId != null ? accessKeyId.trim() : "";
        String secret = secretKey != null ? secretKey.trim() : "";
        String token = sessionToken != null ? sessionToken.trim() : "";

        if (!key.isEmpty() && !secret.isEmpty()) {
            if (!token.isEmpty()) {
                log.info("Configuring AWS static session credentials for region {}", region);
                return StaticCredentialsProvider.create(AwsSessionCredentials.create(key, secret, token));
            }
            if (key.startsWith("ASIA")) {
                log.warn("AWS_ACCESS_KEY_ID begins with 'ASIA' (temporary STS credential) but no AWS_SESSION_TOKEN is provided.");
            }
            log.info("Configuring AWS static basic credentials for region {}", region);
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret));
        }
        log.info("Using AWS DefaultCredentialsProvider for region {}", region);
        return DefaultCredentialsProvider.create();
    }
}

