package com.bidmyhobby.auction.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.api.gax.paging.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class BidStorageService {

    private static final Logger logger = LoggerFactory.getLogger(BidStorageService.class);
    private final S3Client s3Client;
    private final Storage gcsClient;
    private final ObjectMapper objectMapper;

    @Value("${gcp.storage.bucket.name:${GCP_STORAGE_BUCKET:${GCS_BUCKET_NAME:}}}")
    private String gcsBucketName;

    @Value("${aws.s3.bucket.name:bidmyhobby-catalog}")
    private String bucketName;

    @Value("${aws.s3.bucket.bids-prefix:bids/}")
    private String bidsPrefix;

    @Value("${aws.s3.region:ap-south-1}")
    private String region;

    public BidStorageService(@Autowired(required = false) S3Client s3Client,
                             @Autowired(required = false) Storage gcsClient) {
        this.s3Client = s3Client;
        this.gcsClient = gcsClient;
        this.objectMapper = new ObjectMapper();
    }

    private boolean isGcsActive() {
        return gcsClient != null && gcsBucketName != null && !gcsBucketName.isBlank();
    }

    private boolean isS3Active() {
        return s3Client != null && bucketName != null && !bucketName.isBlank() && !bucketName.contains("dummy");
    }

    public void saveBid(String itemId, String bidderEmail, double bidAmount) throws Exception {
        try {
            String bidId = UUID.randomUUID().toString();
            String bidKey = bidsPrefix + itemId + "/" + bidId + ".json";

            Map<String, Object> bidData = new HashMap<>();
            bidData.put("bidId", bidId);
            bidData.put("itemId", itemId);
            bidData.put("bidderEmail", bidderEmail);
            bidData.put("bidAmount", bidAmount);
            bidData.put("currency", "INR");
            bidData.put("timestamp", System.currentTimeMillis());
            bidData.put("status", "ACTIVE");

            String jsonContent = objectMapper.writeValueAsString(bidData);

            if (isGcsActive()) {
                BlobId blobId = BlobId.of(gcsBucketName, bidKey);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("application/json").build();
                gcsClient.create(blobInfo, jsonContent.getBytes(StandardCharsets.UTF_8));
                logger.info("✅ Saved bid to GCS - BidID: {}, ItemID: {}, Bidder: {}", bidId, itemId, bidderEmail);
            } else if (isS3Active()) {
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(bidKey)
                        .contentType("application/json")
                        .build();
                s3Client.putObject(request, RequestBody.fromString(jsonContent));
                logger.info("✅ Saved bid to S3 - BidID: {}, ItemID: {}, Bidder: {}", bidId, itemId, bidderEmail);
            } else {
                Path target = Paths.get("data", "storage", bidKey);
                Files.createDirectories(target.getParent());
                Files.writeString(target, jsonContent, StandardCharsets.UTF_8);
                logger.info("✅ Saved bid to local storage - BidID: {}, ItemID: {}, Bidder: {}", bidId, itemId, bidderEmail);
            }
        } catch (Exception e) {
            logger.error("❌ Unexpected error saving bid", e);
            throw e;
        }
    }

    public List<Map<String, Object>> getBidsForItem(String itemId) {
        List<Map<String, Object>> bids = new ArrayList<>();
        String prefix = bidsPrefix + itemId + "/";

        if (isGcsActive()) {
            try {
                Page<Blob> blobs = gcsClient.list(gcsBucketName, Storage.BlobListOption.prefix(prefix));
                for (Blob blob : blobs.iterateAll()) {
                    if (blob.getName().endsWith(".json")) {
                        try {
                            Map<String, Object> bidData = objectMapper.readValue(blob.getContent(), Map.class);
                            bids.add(bidData);
                        } catch (Exception ex) {
                            logger.warn("Error parsing GCS bid data: {}", ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error retrieving bids from GCS for item {}", itemId, e);
            }
        } else if (isS3Active()) {
            try {
                ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefix)
                        .build();

                ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
                for (S3Object s3Object : listResponse.contents()) {
                    try {
                        GetObjectRequest getRequest = GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(s3Object.key())
                                .build();
                        String content = s3Client.getObjectAsBytes(getRequest).asUtf8String();
                        Map<String, Object> bidData = objectMapper.readValue(content, Map.class);
                        bids.add(bidData);
                    } catch (Exception e) {
                        logger.warn("Error parsing S3 bid data: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("Error retrieving bids from S3 for item {}", itemId, e);
            }
        } else {
            try {
                Path dir = Paths.get("data", "storage", prefix);
                if (Files.exists(dir)) {
                    try (var stream = Files.list(dir)) {
                        stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                            try {
                                Map<String, Object> bidData = objectMapper.readValue(Files.readString(p), Map.class);
                                bids.add(bidData);
                            } catch (Exception ignored) {}
                        });
                    }
                }
            } catch (Exception e) {
                logger.error("Error retrieving bids from local storage for item {}", itemId, e);
            }
        }

        bids.sort((a, b) -> Long.compare((Long) b.get("timestamp"), (Long) a.get("timestamp")));
        return bids;
    }

    public List<Map<String, Object>> getBidsByUser(String userEmail) {
        List<Map<String, Object>> userBids = new ArrayList<>();

        if (isGcsActive()) {
            try {
                Page<Blob> blobs = gcsClient.list(gcsBucketName, Storage.BlobListOption.prefix(bidsPrefix));
                for (Blob blob : blobs.iterateAll()) {
                    if (blob.getName().endsWith(".json")) {
                        try {
                            Map<String, Object> bidData = objectMapper.readValue(blob.getContent(), Map.class);
                            if (userEmail.equals(bidData.get("bidderEmail"))) {
                                userBids.add(bidData);
                            }
                        } catch (Exception ex) {
                            logger.warn("Error parsing GCS user bid: {}", ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error retrieving user bids from GCS: {}", e.getMessage());
            }
        } else if (isS3Active()) {
            try {
                ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(bidsPrefix)
                        .build();

                ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
                for (S3Object s3Object : listResponse.contents()) {
                    try {
                        GetObjectRequest getRequest = GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(s3Object.key())
                                .build();
                        String content = s3Client.getObjectAsBytes(getRequest).asUtf8String();
                        Map<String, Object> bidData = objectMapper.readValue(content, Map.class);
                        if (userEmail.equals(bidData.get("bidderEmail"))) {
                            userBids.add(bidData);
                        }
                    } catch (Exception e) {
                        logger.warn("Error parsing S3 user bid: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("Error retrieving user bids from S3: {}", e.getMessage());
            }
        } else {
            try {
                Path dir = Paths.get("data", "storage", bidsPrefix);
                if (Files.exists(dir)) {
                    try (var walk = Files.walk(dir)) {
                        walk.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                            try {
                                Map<String, Object> bidData = objectMapper.readValue(Files.readString(p), Map.class);
                                if (userEmail.equals(bidData.get("bidderEmail"))) {
                                    userBids.add(bidData);
                                }
                            } catch (Exception ignored) {}
                        });
                    }
                }
            } catch (Exception e) {
                logger.error("Error retrieving user bids from local storage: {}", e.getMessage());
            }
        }

        userBids.sort((a, b) -> Long.compare((Long) b.get("timestamp"), (Long) a.get("timestamp")));
        return userBids;
    }
}
