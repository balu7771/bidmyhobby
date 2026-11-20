package com.bidmyhobby.hobbyaibackend.config;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final Storage gcsClient;

    @Value("${gcp.storage.bucket.name:${GCP_STORAGE_BUCKET:${GCS_BUCKET_NAME:}}}")
    private String gcsBucketName;

    @Value("${aws.s3.ai.bucket:}")
    private String bucketName;

    @Value("${aws.s3.images-prefix:images/}")
    private String imagesPrefix;

    @Value("${aws.s3.profiles-key:profiles.json}")
    private String profilesKey;

    public S3Service(@Autowired(required = false) S3Client s3Client,
                     @Autowired(required = false) Storage gcsClient) {
        this.s3Client = s3Client;
        this.gcsClient = gcsClient;
    }

    public InputStream getProfilesJson() throws IOException {
        // 1. Try GCS if configured
        if (gcsClient != null && gcsBucketName != null && !gcsBucketName.isBlank()) {
            try {
                Blob blob = gcsClient.get(BlobId.of(gcsBucketName, profilesKey));
                if (blob != null && blob.exists()) {
                    System.out.println("✅ Loaded profiles from GCS bucket: " + gcsBucketName);
                    return new ByteArrayInputStream(blob.getContent());
                }
            } catch (Exception e) {
                System.out.println("⚠️ Could not load profiles from GCS (" + e.getMessage() + "), trying fallbacks");
            }
        }

        // 2. Try S3 if configured
        try {
            if (s3Client != null && bucketName != null && !bucketName.isBlank() && !bucketName.contains("dummy")) {
                GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(profilesKey)
                    .build();
                return s3Client.getObject(request);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not load profiles from S3 (" + e.getMessage() + "), falling back to bundled profiles.json");
        }

        // 3. Fallback: check classpath for bundled profiles.json
        InputStream cpStream = getClass().getResourceAsStream("/" + profilesKey);
        if (cpStream != null) {
            System.out.println("✅ Loaded profiles from bundled classpath: /" + profilesKey);
            return cpStream;
        }

        // 4. Fallback: check local data directory
        java.io.File localFile = new java.io.File("data/" + profilesKey);
        if (localFile.exists()) {
            System.out.println("✅ Loaded profiles from local file: " + localFile.getAbsolutePath());
            return new java.io.FileInputStream(localFile);
        }

        throw new IOException("profiles.json could not be found in GCS, S3, classpath, or data directory");
    }

    public String getImageUrl(String fileName) {
        return fileName;
    }
}
