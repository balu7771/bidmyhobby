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
        try {
            if (bucketName != null && !bucketName.isBlank() && !bucketName.contains("dummy")) {
                GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(profilesKey)
                    .build();
                return s3Client.getObject(request);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not load profiles from S3 (" + e.getMessage() + "), falling back to bundled profiles.json");
        }

        // Fallback: check classpath for bundled profiles.json
        InputStream cpStream = getClass().getResourceAsStream("/" + profilesKey);
        if (cpStream != null) {
            System.out.println("✅ Loaded profiles from bundled classpath: /" + profilesKey);
            return cpStream;
        }

        // Fallback: check local data directory
        java.io.File localFile = new java.io.File("data/" + profilesKey);
        if (localFile.exists()) {
            System.out.println("✅ Loaded profiles from local file: " + localFile.getAbsolutePath());
            return new java.io.FileInputStream(localFile);
        }

        throw new IOException("profiles.json could not be found in S3, classpath, or data directory");
    }

    public String getImageUrl(String fileName) {
        return fileName;
    }
}
