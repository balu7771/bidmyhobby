package com.bidmyhobby.catalog.service;

import com.bidmyhobby.catalog.model.Item;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.api.gax.paging.Page;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class S3StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3Client;
    private final Storage gcsClient;
    private final ObjectMapper objectMapper;

    @Value("${gcp.storage.bucket.name:${GCP_STORAGE_BUCKET:${GCS_BUCKET_NAME:}}}")
    private String gcsBucketName;

    @Value("${aws.s3.bucket.name:bidmyhobby-catalog}")
    private String bucketName;

    @Value("${aws.s3.bucket.metadata-prefix:metadata/}")
    private String metadataPrefix;

    @Value("${aws.s3.bucket.images-prefix:images/}")
    private String imagesPrefix;

    @Value("${aws.region:ap-south-1}")
    private String region;

    @Value("${aws.s3.health-check.enabled:true}")
    private boolean healthCheckEnabled;

    private volatile boolean s3Available = true;

    private final Map<String, Item> itemCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, java.time.Instant> itemLastModifiedCache = new java.util.concurrent.ConcurrentHashMap<>();

    public S3StorageService(@Autowired(required = false) S3Client s3Client,
                            @Autowired(required = false) Storage gcsClient) {
        this.s3Client = s3Client;
        this.gcsClient = gcsClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public boolean isGcsActive() {
        return gcsClient != null && gcsBucketName != null && !gcsBucketName.isBlank();
    }

    public boolean isS3Active() {
        return s3Available && s3Client != null && bucketName != null && !bucketName.isBlank() && !bucketName.contains("dummy");
    }

    @PostConstruct
    public void init() {
        logger.info("═══════════════════════════════════════════════════════════════");
        if (isGcsActive()) {
            logger.info("✅ Storage Provider: Google Cloud Storage (GCS)");
            logger.info("   Bucket Name: {}", gcsBucketName);
        } else if (isS3Active()) {
            logger.info("✅ Storage Provider: AWS S3");
            logger.info("   Bucket Name: {}", bucketName);
            logger.info("   Region: {}", region);
        } else {
            logger.info("✅ Storage Provider: Local Disk Storage");
            logger.info("   Directory: data/storage/");
        }
        logger.info("   Metadata Prefix: {}", metadataPrefix);
        logger.info("   Images Prefix: {}", imagesPrefix);
        logger.info("═══════════════════════════════════════════════════════════════");

        try {
            Path localDir = Paths.get("data", "storage");
            Files.createDirectories(localDir);
            logger.info("✅ Local Disk Storage directory verified: {}", localDir.toAbsolutePath());
        } catch (Exception e) {
            logger.warn("⚠️ Could not initialize local storage dir: {}", e.getMessage());
        }

        if (!healthCheckEnabled) return;

        new Thread(() -> {
            try {
                Thread.sleep(1000);
                if (isGcsActive()) {
                    try {
                        Page<Blob> page = gcsClient.list(gcsBucketName, Storage.BlobListOption.pageSize(1));
                        logger.info("✅ GCS Health Check PASSED for bucket: {}", gcsBucketName);
                    } catch (Exception e) {
                        logger.warn("⚠️ GCS Health Check warning for bucket {}: {}. Will fallback to local storage if needed.", gcsBucketName, e.getMessage());
                    }
                } else if (isS3Active()) {
                    try {
                        ListObjectsV2Request healthCheckRequest = ListObjectsV2Request.builder()
                                .bucket(bucketName)
                                .maxKeys(1)
                                .build();
                        ListObjectsV2Response response = s3Client.listObjectsV2(healthCheckRequest);
                        logger.info("✅ S3 Health Check PASSED - Total objects: ~{}", response.keyCount());
                    } catch (Exception e) {
                        logger.warn("⚠️ S3 Health Check FAILED ({}). Disabling S3; falling back to local disk storage.", e.getMessage());
                        s3Available = false;
                    }
                }
            } catch (Exception e) {
                logger.warn("⚠️ Storage Health Check non-blocking notice: {}", e.getMessage());
            }
        }, "Storage-HealthCheck-Thread").start();
    }

    public String uploadFile(MultipartFile file, byte[] processedImageBytes, String contentType) throws IOException {
        String filename = imagesPrefix + System.currentTimeMillis() + "-" + file.getOriginalFilename();
        byte[] content = processedImageBytes != null ? processedImageBytes : file.getBytes();
        String ct = contentType != null ? contentType : (file.getContentType() != null ? file.getContentType() : "image/jpeg");
        return uploadFile(filename, content, ct);
    }

    public String uploadFile(String key, byte[] content, String contentType) {
        String ct = contentType != null ? contentType : "image/jpeg";

        if (isGcsActive()) {
            try {
                BlobId blobId = BlobId.of(gcsBucketName, key);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(ct).build();
                gcsClient.create(blobInfo, content);
                logger.info("✅ Uploaded file to GCS: gs://{}/{}", gcsBucketName, key);
                return key;
            } catch (Exception e) {
                logger.warn("⚠️ GCS upload failed for {} ({}). Falling back to local storage.", key, e.getMessage());
                return saveFileToLocal(key, content);
            }
        } else if (isS3Active()) {
            try {
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(ct)
                        .build();
                s3Client.putObject(request, RequestBody.fromBytes(content));
                logger.info("✅ Uploaded file to S3: s3://{}/{}", bucketName, key);
                return key;
            } catch (Exception e) {
                logger.warn("⚠️ S3 upload failed for {} ({}). Disabling S3, falling back to local storage.", key, e.getMessage());
                s3Available = false;
                return saveFileToLocal(key, content);
            }
        } else {
            return saveFileToLocal(key, content);
        }
    }

    private String saveFileToLocal(String key, byte[] content) {
        try {
            Path target = Paths.get("data", "storage", key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            logger.info("✅ Saved file to local storage: {}", target.toAbsolutePath());
            return key;
        } catch (IOException e) {
            logger.error("❌ Local storage write failed for {}: {}", key, e.getMessage(), e);
            throw new RuntimeException("Storage write failed: " + e.getMessage(), e);
        }
    }

    public String uploadImage(byte[] processedImageData, String folder) {
        String filename = (folder != null ? folder : imagesPrefix) + "/" + System.currentTimeMillis() + ".jpg";
        uploadFile(filename, processedImageData, "image/jpeg");
        return generateImageUrl(filename);
    }

    public Item saveItem(Item item) throws IOException {
        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }

        String metadataKey = metadataPrefix + "items/" + item.getItemId() + ".json";
        String jsonContent = objectMapper.writeValueAsString(item);

        if (isGcsActive()) {
            try {
                BlobId blobId = BlobId.of(gcsBucketName, metadataKey);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("application/json").build();
                gcsClient.create(blobInfo, jsonContent.getBytes(StandardCharsets.UTF_8));
                logger.info("✅ Saved item to GCS: gs://{}/{}", gcsBucketName, metadataKey);
            } catch (Exception e) {
                logger.warn("⚠️ GCS saveItem failed for {} ({}). Falling back to local storage.", item.getItemId(), e.getMessage());
                saveItemToLocal(metadataKey, jsonContent);
            }
        } else if (isS3Active()) {
            try {
                PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(metadataKey)
                        .contentType("application/json")
                        .build();
                s3Client.putObject(request, RequestBody.fromString(jsonContent));
                logger.info("✅ Saved item to S3: s3://{}/{}", bucketName, metadataKey);
            } catch (Exception e) {
                logger.warn("⚠️ S3 saveItem failed for {} ({}). Disabling S3, falling back to local storage.", item.getItemId(), e.getMessage());
                s3Available = false;
                saveItemToLocal(metadataKey, jsonContent);
            }
        } else {
            saveItemToLocal(metadataKey, jsonContent);
        }

        itemCache.put(item.getItemId(), item);
        itemLastModifiedCache.put(item.getItemId(), java.time.Instant.now());
        return item;
    }

    private void saveItemToLocal(String metadataKey, String jsonContent) throws IOException {
        Path target = Paths.get("data", "storage", metadataKey);
        Files.createDirectories(target.getParent());
        Files.writeString(target, jsonContent, StandardCharsets.UTF_8);
        logger.info("✅ Saved item metadata to local storage: {}", target.toAbsolutePath());
    }

    public Item getItem(String itemId) {
        Item cached = itemCache.get(itemId);
        if (cached != null) return cached;

        String metadataKey = metadataPrefix + "items/" + itemId + ".json";

        if (isGcsActive()) {
            try {
                Blob blob = gcsClient.get(BlobId.of(gcsBucketName, metadataKey));
                if (blob != null && blob.exists()) {
                    Item item = objectMapper.readValue(blob.getContent(), Item.class);
                    if (item != null) {
                        if (item.getImageUrl() == null) item.setImageUrl(generateImageUrl(itemId));
                        itemCache.put(itemId, item);
                        return item;
                    }
                }
            } catch (Exception e) {
                logger.debug("GCS getItem miss for {}: {}", itemId, e.getMessage());
            }
        }

        if (isS3Active()) {
            try {
                GetObjectRequest request = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(metadataKey)
                        .build();
                ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
                Item item = objectMapper.readValue(response, Item.class);
                if (item != null) {
                    if (item.getImageUrl() == null) item.setImageUrl(generateImageUrl(itemId));
                    itemCache.put(itemId, item);
                    return item;
                }
            } catch (Exception e) {
                logger.debug("S3 getItem miss for {}: {}", itemId, e.getMessage());
            }
        }

        try {
            Path target = Paths.get("data", "storage", metadataKey);
            if (Files.exists(target)) {
                Item item = objectMapper.readValue(Files.readString(target, StandardCharsets.UTF_8), Item.class);
                if (item != null) {
                    if (item.getImageUrl() == null) item.setImageUrl(generateImageUrl(itemId));
                    itemCache.put(itemId, item);
                    return item;
                }
            }
        } catch (Exception e) {
            logger.debug("Local storage getItem miss for {}: {}", itemId, e.getMessage());
        }

        return null;
    }

    public List<Item> getAllItems() {
        Map<String, Item> itemsMap = new LinkedHashMap<>();

        if (isGcsActive()) {
            try {
                Page<Blob> blobs = gcsClient.list(gcsBucketName, Storage.BlobListOption.prefix(metadataPrefix + "items/"));
                for (Blob blob : blobs.iterateAll()) {
                    if (blob.getName().endsWith(".json")) {
                        try {
                            Item item = objectMapper.readValue(blob.getContent(), Item.class);
                            if (item != null && !"DELETED".equals(item.getStatus())) {
                                if (item.getImageUrl() == null) item.setImageUrl(generateImageUrl(item.getItemId()));
                                itemsMap.put(item.getItemId(), item);
                                itemCache.put(item.getItemId(), item);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                logger.warn("GCS getAllItems notice: {}", e.getMessage());
            }
        }

        if (isS3Active()) {
            try {
                ListObjectsV2Request request = ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(metadataPrefix + "items/")
                        .build();
                ListObjectsV2Response response = s3Client.listObjectsV2(request);
                for (S3Object s3Object : response.contents()) {
                    if (s3Object.key().endsWith(".json")) {
                        try {
                            GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucketName).key(s3Object.key()).build();
                            ResponseInputStream<GetObjectResponse> s3in = s3Client.getObject(getReq);
                            Item item = objectMapper.readValue(s3in, Item.class);
                            if (item != null && !"DELETED".equals(item.getStatus())) {
                                if (item.getImageUrl() == null) item.setImageUrl(generateImageUrl(item.getItemId()));
                                itemsMap.putIfAbsent(item.getItemId(), item);
                                itemCache.put(item.getItemId(), item);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                logger.warn("S3 getAllItems notice ({}). Disabling S3.", e.getMessage());
                s3Available = false;
            }
        }

        try {
            Path dir = Paths.get("data", "storage", metadataPrefix + "items");
            if (Files.exists(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try {
                            Item item = objectMapper.readValue(Files.readString(p, StandardCharsets.UTF_8), Item.class);
                            if (item != null && !"DELETED".equals(item.getStatus())) {
                                if (item.getImageUrl() == null) item.setImageUrl(generateImageUrl(item.getItemId()));
                                itemsMap.putIfAbsent(item.getItemId(), item);
                                itemCache.put(item.getItemId(), item);
                            }
                        } catch (Exception ignored) {}
                    });
                }
            }
        } catch (Exception e) {
            logger.warn("Local storage getAllItems notice: {}", e.getMessage());
        }

        List<Item> sortedItems = new ArrayList<>(itemsMap.values());
        sortedItems.sort((a, b) -> {
            if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
            if (a.getCreatedAt() == null) return 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });
        return sortedItems;
    }

    public String generateImageUrl(String imageKey) {
        if (isGcsActive()) {
            return String.format("https://storage.googleapis.com/%s/%s", gcsBucketName, imageKey);
        } else if (isS3Active()) {
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, imageKey);
        } else {
            String clean = imageKey.startsWith("images/") ? imageKey.substring("images/".length()) : imageKey;
            return "/api/catalog/images/" + clean;
        }
    }

    public byte[] getImageBytes(String key) {
        if (isGcsActive()) {
            try {
                Blob blob = gcsClient.get(BlobId.of(gcsBucketName, key));
                if (blob != null && blob.exists()) {
                    return blob.getContent();
                }
            } catch (Exception e) {
                logger.debug("GCS getImageBytes notice for {}: {}", key, e.getMessage());
            }
        }
        if (isS3Active()) {
            try {
                GetObjectRequest req = GetObjectRequest.builder().bucket(bucketName).key(key).build();
                ResponseBytes<GetObjectResponse> obj = s3Client.getObjectAsBytes(req);
                return obj.asByteArray();
            } catch (Exception e) {
                logger.debug("S3 getImageBytes notice for {}: {}", key, e.getMessage());
            }
        }
        try {
            Path p = Paths.get("data", "storage", key);
            if (Files.exists(p)) return Files.readAllBytes(p);
        } catch (Exception ignored) {}
        return null;
    }

    public List<Item> getItemsByCreator(String email) {
        return getAllItems().stream()
                .filter(item -> email.equals(item.getCreatorEmail()) && !"DELETED".equals(item.getStatus()))
                .toList();
    }

    public List<Item> getActiveItems() {
        return getAllItems().stream()
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .toList();
    }

    public boolean deleteItem(String itemId) {
        try {
            Item item = getItem(itemId);
            if (item != null) {
                if (item.getImageUrl() != null) {
                    String key = extractKeyFromUrl(item.getImageUrl());
                    if (key != null) deleteObject(key);
                }
                if (item.getImageUrls() != null) {
                    for (String url : item.getImageUrls()) {
                        String key = extractKeyFromUrl(url);
                        if (key != null) deleteObject(key);
                    }
                }
                item.setStatus("DELETED");
                saveItem(item);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error deleting item {}: {}", itemId, e.getMessage());
            return false;
        }
    }

    private String extractKeyFromUrl(String url) {
        try {
            int idx = url.indexOf(".com/");
            if (idx != -1 && idx + 5 < url.length()) {
                return url.substring(idx + 5);
            }
            int apiIdx = url.indexOf("/images/");
            if (apiIdx != -1) {
                return url.substring(apiIdx + 1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void deleteObject(String key) {
        try {
            if (isGcsActive()) {
                gcsClient.delete(BlobId.of(gcsBucketName, key));
                logger.info("🗑️ Deleted GCS blob: gs://{}/{}", gcsBucketName, key);
            }
        } catch (Exception e) {
            logger.warn("Failed to delete GCS blob {}: {}", key, e.getMessage());
        }

        try {
            if (isS3Active()) {
                DeleteObjectRequest deleteReq = DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                s3Client.deleteObject(deleteReq);
                logger.info("🗑️ Deleted S3 object: {}", key);
            }
        } catch (Exception e) {
            logger.warn("Failed to delete S3 object {}: {}", key, e.getMessage());
        }

        try {
            Path p = Paths.get("data", "storage", key);
            Files.deleteIfExists(p);
            logger.info("🗑️ Deleted local storage file: {}", p.toAbsolutePath());
        } catch (Exception e) {
            logger.warn("Failed to delete local file {}: {}", key, e.getMessage());
        }
    }
}
