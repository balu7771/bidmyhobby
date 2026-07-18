package com.bidmyhobby.hobbyaibackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;

@Service
public class S3Service {

    private final S3Client s3Client;

    @Value("${aws.s3.ai.bucket}")
    private String bucketName;

    @Value("${aws.s3.images-prefix:images/}")
    private String imagesPrefix;

    @Value("${aws.s3.profiles-key:profiles.json}")
    private String profilesKey;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public InputStream getProfilesJson() throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(profilesKey)
            .build();
        return s3Client.getObject(request);
    }

    public String getImageUrl(String fileName) {
        return fileName;
    }
}
