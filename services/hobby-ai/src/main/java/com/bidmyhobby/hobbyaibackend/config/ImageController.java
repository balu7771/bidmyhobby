package com.bidmyhobby.hobbyaibackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@RestController
@RequestMapping("/api/images")
@CrossOrigin(origins = "*")
public class ImageController {

    @Value("${aws.s3.ai.bucket}")
    private String bucketName;

    @Value("${aws.s3.images-prefix:images/}")
    private String imagesPrefix;

    private final S3Client s3Client;

    public ImageController(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        try {
            // Try with configured prefix first
            String primaryKey = (imagesPrefix != null ? imagesPrefix : "") + filename;
            System.out.println("🔍 Attempt 1: Fetching " + primaryKey + " from bucket " + bucketName);
            
            try {
                return fetchFromS3(primaryKey, filename);
            } catch (Exception e1) {
                // If primary failed, try root (if primary wasn't already root)
                if (imagesPrefix != null && !imagesPrefix.isEmpty() && !imagesPrefix.equals("/")) {
                    System.out.println("⚠️ Not found at " + primaryKey + ". Attempt 2: Fetching from root: " + filename);
                    try {
                        return fetchFromS3(filename, filename);
                    } catch (Exception e2) {
                        // Keep going
                    }
                }
                
                // If still not found, try "images/" folder as a last resort if it wasn't already tried
                if (!"images/".equals(imagesPrefix)) {
                    String lastResortKey = "images/" + filename;
                    System.out.println("⚠️ Not found at root. Attempt 3: Fetching from last resort: " + lastResortKey);
                    return fetchFromS3(lastResortKey, filename);
                }
                
                throw e1;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to fetch image " + filename + ": " + e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<byte[]> fetchFromS3(String key, String filename) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
        
        String contentType = objectBytes.response().contentType();
        if (contentType == null || contentType.isEmpty() || "binary/octet-stream".equals(contentType)) {
            contentType = determineContentType(filename);
        }
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(objectBytes.asByteArray());
    }
    
    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}