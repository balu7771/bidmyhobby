package com.bidmyhobby.catalog.controller;

import com.bidmyhobby.catalog.event.NotificationPublisher;
import com.bidmyhobby.catalog.auth.AuthenticatedUser;
import com.bidmyhobby.catalog.model.Item;
import com.bidmyhobby.catalog.service.CatalogService;
import com.bidmyhobby.catalog.service.ItemProcessingService;
import com.bidmyhobby.catalog.service.S3StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/catalog")
@CrossOrigin(origins = "*")
public class CatalogController {

    @Autowired
    CatalogService catalogService;

    @Autowired
    S3StorageService s3StorageService;

    @Autowired
    NotificationPublisher notificationPublisher;

    @Value("${aws.s3.bucket.name:bidmyhobby-catalog}")
    private String bucketName;

    @Autowired
    S3Client s3Client;
    @Autowired
    AuthenticatedUser authenticatedUser;

    // @Autowired
    // ItemProcessingService itemProcessingService; // Commented out for now

    @GetMapping("/items")
    public ResponseEntity<List<Item>> getAllItems() {
        return ResponseEntity.ok(catalogService.getAllItems());
    }

    @GetMapping("/items/active")
    public ResponseEntity<List<Item>> getActiveItems() {
        return ResponseEntity.ok(catalogService.getActiveItems());
    }

    @GetMapping("/item/{itemId}")
    public ResponseEntity<Item> getItem(@PathVariable String itemId) {
        Item item = catalogService.getItem(itemId);
        if (item != null) {
            return ResponseEntity.ok(item);
        }
        return ResponseEntity.notFound().build();
    }

    /*
     * @PostMapping("/items")
     * public ResponseEntity<Item> createItem(@RequestBody Item item){
     * return ResponseEntity.ok(catalogService.createItem(item));
     * }
     */

    @GetMapping("/items/{itemId}/creator/{email}")
    public ResponseEntity<List<Item>> getItemsByCreator(@PathVariable String email) {
        return ResponseEntity.ok(catalogService.getItemsByCreator(email));
    }

    @PutMapping("/items/{itemId}/status")
    public ResponseEntity<?> updateItemStatus(@PathVariable String itemId,
            @RequestBody Map<String, String> statusUpdate) {
        try {
            String newStatus = statusUpdate.get("status");
            if (newStatus == null) {
                return ResponseEntity.badRequest().body("Status is required");
            }

            Item item = catalogService.getItem(itemId);
            if (item == null) {
                return ResponseEntity.notFound().build();
            }

            item.setStatus(newStatus);
            catalogService.saveProcessedItem(item);

            return ResponseEntity.ok().body("Item status updated to: " + newStatus);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/items/{itemId}/sale-in-progress")
    public ResponseEntity<?> markSaleInProgress(@PathVariable String itemId, @RequestParam String email) {
        try {
            Item item = catalogService.getItem(itemId);
            if (item == null) {
                return ResponseEntity.notFound().build();
            }

            // Check if the user is the creator of the item
            if (!item.getCreatorEmail().equals(email)) {
                return ResponseEntity.badRequest().body("Only item creator can mark sale as in progress");
            }

            // Update item status to SALE_IN_PROGRESS
            item.setStatus("SALE_IN_PROGRESS");
            catalogService.saveProcessedItem(item);

            return ResponseEntity.ok().body("Item marked as sale in progress");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<?> deleteItem(@PathVariable String itemId, @RequestParam(required = false) String email,
            @CookieValue("bmh_session") String token) {
        try {
            var identity = authenticatedUser.require(token);
            boolean deleted = catalogService.deleteItem(itemId, identity.email(), identity.isAdmin());
            if (deleted) {
                return ResponseEntity.ok().body("Item deleted successfully");
            } else {
                return ResponseEntity.badRequest().body("Failed to delete item");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @DeleteMapping("/items/images/{filename:.+}")
    public ResponseEntity<?> deleteItemByFilename(@PathVariable String filename, @RequestParam(required = false) String email,
            @CookieValue("bmh_session") String token) {
        try {
            // Strip timestamp prefix to get the actual itemId stored in DB
            String itemId = filename.contains(".") ? filename.substring(0, filename.lastIndexOf(".")) : filename;
            var identity = authenticatedUser.require(token);
            boolean deleted = catalogService.deleteItem(itemId, identity.email(), identity.isAdmin());
            if (deleted) {
                return ResponseEntity.ok().body("Item deleted successfully");
            } else {
                return ResponseEntity.badRequest().body("Failed to delete item");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/items/{itemId}/mark-sold")
    public ResponseEntity<?> markAsSold(@PathVariable String itemId, @RequestParam(required = false) String email,
            @CookieValue("bmh_session") String token) {
        try {
            String authenticatedEmail = authenticatedUser.require(token).email();
            Item item = catalogService.markAsSold(itemId, authenticatedEmail);

            // Send sold notification email
            notificationPublisher.publishItemSoldEvent(
                    item.getItemId(),
                    authenticatedEmail,
                    item.getName());

            return ResponseEntity.ok().body("Item marked as sold");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/items/{itemId}/sold")
    public ResponseEntity<?> markSold(@PathVariable String itemId, @RequestParam(required = false) String email,
            @CookieValue("bmh_session") String token) {
        // Extract itemId from filename if it contains extension
        String cleanItemId = itemId.contains(".") ? itemId.substring(0, itemId.lastIndexOf(".")) : itemId;
        return markAsSold(cleanItemId, email, token);
    }

    /**
     * Image proxy: serves catalog images from S3 through the backend so the
     * S3 bucket does NOT need public read access.
     * URL pattern: GET /api/catalog/images/{filename}
     * Also accepts full S3 URL paths split by nginx as a fallback.
     */
    @GetMapping("/images/{filename:.+}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        String[] candidates = {
            "images/" + filename,
            filename
        };
        for (String key : candidates) {
            try {
                GetObjectRequest req = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                ResponseBytes<GetObjectResponse> obj = s3Client.getObjectAsBytes(req);
                String ct = obj.response().contentType();
                if (ct == null || ct.isBlank() || "binary/octet-stream".equals(ct)) {
                    String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";
                    ct = switch (ext) {
                        case "jpg", "jpeg" -> "image/jpeg";
                        case "png" -> "image/png";
                        case "gif" -> "image/gif";
                        case "webp" -> "image/webp";
                        case "pdf" -> "application/pdf";
                        default -> "image/jpeg";
                    };
                }
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(ct))
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                        .body(obj.asByteArray());
            } catch (Exception ignored) {}
        }
        return ResponseEntity.notFound().build();
    }
}
