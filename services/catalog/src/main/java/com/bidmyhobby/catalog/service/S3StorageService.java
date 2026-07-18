package com.bidmyhobby.catalog.service;

import com.bidmyhobby.catalog.model.Item;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class S3StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.bucket.name:bidmyhobby-catalog}")
    private String bucketName;

    @Value("${aws.s3.bucket.metadata-prefix:metadata/}")
    private String metadataPrefix;

    @Value("${aws.s3.bucket.images-prefix:images/}")
    private String imagesPrefix;

    @Value("${aws.region:us-east-1}")
    private String region;

    private final java.util.Map<String, Item> itemCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, java.time.Instant> itemLastModifiedCache = new java.util.concurrent.ConcurrentHashMap<>();

    public S3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("✅ S3 Storage Service Initialized");
        logger.info("   Bucket Name: {}", bucketName);
        logger.info("   Region: {}", region);
        logger.info("   Metadata Prefix: {}", metadataPrefix);
        logger.info("   Images Prefix: {}", imagesPrefix);
        logger.info("═══════════════════════════════════════════════════════════════");

        // Health check: Run in background thread to avoid blocking startup
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait 2 sec for S3 client to initialize

                ListObjectsV2Request healthCheckRequest = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .maxKeys(1)
                        .build();

                ListObjectsV2Response response = s3Client.listObjectsV2(healthCheckRequest);

                logger.info("✅ S3 Health Check PASSED");
                logger.info("   Successfully listed objects in bucket: {}", bucketName);
                logger.info("   Total objects in bucket: ~{}", response.keyCount());
            } catch (NoSuchBucketException e) {
                logger.error("❌ S3 Health Check FAILED - Bucket not found!");
                logger.error("   Bucket Name: {}", bucketName);
                logger.error("   Region: {}", region);
                logger.error("   Error: Bucket does not exist or is in a different region/account");
                logger.error("   Action: Verify AWS_S3_BUCKET_NAME and AWS_DEFAULT_REGION");
            } catch (S3Exception e) {
                logger.error("❌ S3 Health Check FAILED - S3 Error");
                logger.error("   Bucket Name: {}", bucketName);
                logger.error("   Region: {}", region);
                logger.error("   Error Code: {}", e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "Unknown");
                logger.error("   Action: Check S3 credentials and bucket access");
            } catch (InterruptedException e) {
                logger.warn("⚠️ S3 Health Check interrupted");
            } catch (Exception e) {
                logger.warn("⚠️ S3 Health Check failed (non-blocking): {}", e.getMessage());
            }
        }, "S3-HealthCheck-Thread").start();
    }

    public String uploadFile(MultipartFile file, byte[] processedImageBytes, String contentType) throws IOException {
        String filename = imagesPrefix + System.currentTimeMillis() + "-" + file.getOriginalFilename();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filename)
                .contentType(contentType != null ? contentType : file.getContentType())
                .build();

        if (processedImageBytes != null) {
            s3Client.putObject(request, RequestBody.fromBytes(processedImageBytes));
        } else {
            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        }

        return filename;
    }

    public String uploadFile(String key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(content));
        return key;
    }

    public String uploadImage(byte[] processedImageData, String folder) {
        String filename = (folder != null ? folder : imagesPrefix) + "/" + System.currentTimeMillis() + ".jpg";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filename)
                .contentType("image/jpeg")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(processedImageData));

        return generateImageUrl(filename);
    }

    public Item saveItem(Item item) throws IOException {
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }

        String metadataKey = metadataPrefix + "items/" + item.getItemId() + ".json";
        String jsonContent = objectMapper.writeValueAsString(item);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(metadataKey)
                .contentType("application/json")
                .build();

        s3Client.putObject(request, RequestBody.fromString(jsonContent));

        // Update Cache
        itemCache.put(item.getItemId(), item);
        itemLastModifiedCache.put(item.getItemId(), java.time.Instant.now());

        return item;
    }

    public Item getItem(String itemId) {
        System.out.println("🔍 getItem called with: " + itemId);

        // Strategy 1: Try with itemId as-is
        try {
            String metadataKey = metadataPrefix + "items/" + itemId + ".json";
            System.out.println("🔍 Strategy 1 - Looking for: " + metadataKey);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(metadataKey)
                    .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            Item item = objectMapper.readValue(response, Item.class);
            if (item != null && item.getImageUrl() == null) {
                item.setImageUrl(generateImageUrl(itemId));
            }
            System.out.println("✅ Strategy 1 SUCCESS: " + item.getName());
            return item;
        } catch (Exception e) {
            System.out.println("❌ Strategy 1 failed: " + e.getMessage());
        }

        // Strategy 2: Try with images/ prefix
        try {
            String metadataKeyWithPrefix = metadataPrefix + "items/" + imagesPrefix + itemId + ".json";
            System.out.println("🔍 Strategy 2 - Looking for: " + metadataKeyWithPrefix);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(metadataKeyWithPrefix)
                    .build();

            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            Item item = objectMapper.readValue(response, Item.class);
            if (item != null && item.getImageUrl() == null) {
                item.setImageUrl(generateImageUrl(imagesPrefix + itemId));
            }
            System.out.println("✅ Strategy 2 SUCCESS: " + item.getName());
            return item;
        } catch (Exception e2) {
            System.out.println("❌ Strategy 2 failed: " + e2.getMessage());
        }

        // Strategy 3: Search all items by matching filename
        try {
            System.out.println("🔍 Strategy 3 - Searching all items for match");
            List<Item> allItems = getAllItems();
            for (Item item : allItems) {
                if (item.getItemId().equals(itemId) ||
                        item.getItemId().endsWith("/" + itemId) ||
                        item.getItemId().contains(itemId)) {
                    System.out.println(
                            "✅ Strategy 3 SUCCESS: Found " + item.getName() + " with itemId: " + item.getItemId());
                    return item;
                }
            }
        } catch (Exception e3) {
            System.out.println("❌ Strategy 3 failed: " + e3.getMessage());
        }

        System.out.println("❌ All strategies failed for itemId: " + itemId);
        return null;
    }

    public List<Item> getAllItems() {
        List<Item> items = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(metadataPrefix + "items/")
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

            for (S3Object s3Object : response.contents()) {
                if (s3Object.key().endsWith(".json")) {
                    String itemId = s3Object.key()
                            .replace(metadataPrefix + "items/", "")
                            .replace(".json", "");
                    
                    java.time.Instant s3LastModified = s3Object.lastModified();
                    Item cachedItem = itemCache.get(itemId);
                    
                    if (cachedItem != null && s3LastModified.equals(itemLastModifiedCache.get(itemId))) {
                        if (!"DELETED".equals(cachedItem.getStatus())) {
                            items.add(cachedItem);
                        }
                    } else {
                        futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                            Item item = getItem(itemId);
                            if (item != null) {
                                itemCache.put(itemId, item);
                                itemLastModifiedCache.put(itemId, s3LastModified);
                                if (!"DELETED".equals(item.getStatus())) {
                                    items.add(item);
                                }
                            }
                        }));
                    }
                }
            }

            // Wait for all fetches to complete
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();

            // Create a copy to safely sort
            List<Item> sortedItems = new ArrayList<>(items);
            // Sort by creation date - newest first
            sortedItems.sort((a, b) -> {
                if (a.getCreatedAt() == null && b.getCreatedAt() == null)
                    return 0;
                if (a.getCreatedAt() == null)
                    return 1;
                if (b.getCreatedAt() == null)
                    return -1;
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            });
            
            return sortedItems;

        } catch (Exception e) {
            // Return empty list on error
        }
        return new ArrayList<>(items);
    }

    public String generateImageUrl(String imageKey) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, imageKey);
    }

    public List<Item> getItemsByCreator(String email) {
        return getAllItems().stream()
                .filter(item -> email.equals(item.getCreatorEmail()) && !"DELETED".equals(item.getStatus()))
                .toList();
    }

    public List<Item> getActiveItems() {
        return getAllItems().stream()
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null)
                        return 0;
                    if (a.getCreatedAt() == null)
                        return 1;
                    if (b.getCreatedAt() == null)
                        return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .toList();
    }

    public boolean deleteItem(String itemId) {
        try {
            // Fetch the item to obtain image URLs
            Item item = getItem(itemId);
            if (item != null) {
                // Delete associated image files from S3 if they exist
                if (item.getImageUrl() != null) {
                    String key = extractKeyFromUrl(item.getImageUrl());
                    if (key != null) {
                        deleteObject(key);
                    }
                }
                if (item.getImageUrls() != null) {
                    for (String url : item.getImageUrls()) {
                        String key = extractKeyFromUrl(url);
                        if (key != null) {
                            deleteObject(key);
                        }
                    }
                }
                // Soft delete: mark item as DELETED
                item.setStatus("DELETED");
                saveItem(item);
                return true;
            }
            return false;
        } catch (Exception e) {
            System.out.println("❌ Error deleting item: " + e.getMessage());
            return false;
        }
    }
    
    // Helper method to extract S3 object key from a full URL
    private String extractKeyFromUrl(String url) {
        try {
            int idx = url.indexOf(".com/");
            if (idx != -1 && idx + 5 < url.length()) {
                return url.substring(idx + 5);
            }
        } catch (Exception e) {
            System.out.println("❌ Failed to extract S3 key: " + e.getMessage());
        }
        return null;
    }

    // Helper method to delete an object from S3
    private void deleteObject(String key) {
        try {
            DeleteObjectRequest deleteReq = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteReq);
            System.out.println("🗑️ Deleted S3 object: " + key);
        } catch (Exception e) {
            System.out.println("❌ Failed to delete S3 object " + key + ": " + e.getMessage());
        }
    }
}
