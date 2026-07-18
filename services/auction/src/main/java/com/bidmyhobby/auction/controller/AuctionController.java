package com.bidmyhobby.auction.controller;

import com.bidmyhobby.auction.client.CatalogServiceClient;
import com.bidmyhobby.auction.client.AiServiceClient;
import com.bidmyhobby.auction.event.BidEventPublisher;
import com.bidmyhobby.auction.service.BidStorageService;
import com.bidmyhobby.auction.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auction")
public class AuctionController {

    @Autowired
    private BidStorageService bidStorageService;

    @Autowired
    private BidEventPublisher bidEventPublisher;

    @Autowired
    private CatalogServiceClient catalogServiceClient;

    @Autowired(required = false)
    private AiServiceClient aiServiceClient;

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Auction Service is working!");
    }

    @PostMapping("/bids")
    public ResponseEntity<?> placeBid(@RequestBody Map<String, Object> bidRequest) {
        try {
            String itemId = (String) bidRequest.get("itemId");
            String bidderEmail = (String) bidRequest.get("bidderEmail");
            BigDecimal bidAmount = new BigDecimal(bidRequest.get("bidAmount").toString());

            // Validate user identity (email or phone number)
            if (bidderEmail == null || bidderEmail.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Valid email or phone number required to place bids");
            }

            // Check item status
            try {
                Map<String, Object> item = catalogServiceClient.getItem(itemId);
                if (item == null) {
                    return ResponseEntity.badRequest().body("Item not found");
                }
                String itemStatus = (String) item.get("status");
                if ("SALE_IN_PROGRESS".equals(itemStatus)) {
                    return ResponseEntity.badRequest().body("Bidding is closed for this item - sale in progress");
                }
            } catch (Exception e) {
                // If catalog service is down, allow bid but log warning
                System.out.println("⚠️ Could not verify item status: " + e.getMessage());
            }

            // Validate bid amount
            if (bidAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body("Bid amount must be greater than 0");
            }

            // Save bid to S3
            bidStorageService.saveBid(itemId, bidderEmail, bidAmount.doubleValue());

            // Publish bid event for notifications
            // TODO: Get actual item owner from catalog service
            bidEventPublisher.publishBidEvent(itemId, bidderEmail, bidAmount.doubleValue(), 
                "balu.learnz@gmail.com", "Auction Item");

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Bid placed successfully");
            response.put("itemId", itemId);
            response.put("bidAmount", bidAmount);
            response.put("currency", "INR");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error placing bid: " + e.getMessage());
        }
    }

    @GetMapping("/bids/{itemId}")
    public ResponseEntity<List<Map<String, Object>>> getBidsForItem(@PathVariable String itemId) {
        try {
            System.out.println("🔍 Getting bids for item: " + itemId);
            List<Map<String, Object>> bids = bidStorageService.getBidsForItem(itemId);
            return ResponseEntity.ok(bids);
        } catch (Exception e) {
            System.err.println("❌ Error getting bids: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
    
    @PostMapping("/bids/get")
    public ResponseEntity<List<Map<String, Object>>> getBidsForItemPost(@RequestBody Map<String, String> request) {
        try {
            String itemId = request.get("itemId");
            System.out.println("🔍 Getting bids for item (POST): " + itemId);
            List<Map<String, Object>> bids = bidStorageService.getBidsForItem(itemId);
            return ResponseEntity.ok(bids);
        } catch (Exception e) {
            System.err.println("❌ Error getting bids: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/bids")
    public ResponseEntity<Map<String, Object>> getAllBids() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Use /bids/{itemId} to get bids for specific item");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/bids/user/{email}")
    public ResponseEntity<List<Map<String, Object>>> getBidsByUser(@PathVariable String email) {
        try {
            System.out.println("🔍 Getting bids for user: " + email);
            List<Map<String, Object>> userBids = bidStorageService.getBidsByUser(email);
            return ResponseEntity.ok(userBids);
        } catch (Exception e) {
            System.err.println("❌ Error getting user bids: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/bids/complete")
    public ResponseEntity<?> completeBidding(@RequestBody Map<String, String> request) {
        String itemId = request.get("itemId");
        try {
            String itemName = request.get("itemName");
            String sellerEmail = request.get("sellerEmail");

            // Validate item exists
            if (itemId == null || itemId.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "INVALID_ITEM_ID");
                error.put("message", "Item ID is required");
                return ResponseEntity.badRequest().body(error);
            }

            List<Map<String, Object>> bids = bidStorageService.getBidsForItem(itemId);
            if (bids.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "BID_NOT_FOUND");
                error.put("message", "No bids found for item");
                return ResponseEntity.status(404).body(error);
            }

            // Find highest bid
            Map<String, Object> winningBid = bids.stream()
                .max(Comparator.comparing(bid -> Double.parseDouble(bid.get("bidAmount").toString())))
                .orElse(null);

            if (winningBid != null) {
                String winnerEmail = (String) winningBid.get("bidderEmail");
                Double winningAmount = Double.parseDouble(winningBid.get("bidAmount").toString());

                // Update item status to SALE_IN_PROGRESS
                try {
                    Map<String, String> statusUpdate = new HashMap<>();
                    statusUpdate.put("status", "SALE_IN_PROGRESS");
                    catalogServiceClient.updateItemStatus(itemId, statusUpdate);
                    System.out.println("✅ Item status updated to SALE_IN_PROGRESS");
                } catch (Exception e) {
                    System.out.println("⚠️ Failed to update item status: " + e.getMessage());
                }

                // Send WhatsApp approval notifications to both buyer and seller
                try {
                    if (aiServiceClient != null && sellerEmail != null) {
                        java.util.Map<String, String> notifyReq = new HashMap<>();
                        notifyReq.put("sellerPhone", sellerEmail);
                        notifyReq.put("buyerPhone",  winnerEmail);
                        notifyReq.put("itemName",    itemName != null ? itemName : "Auction Item");
                        aiServiceClient.notifyApproval(notifyReq);
                        System.out.println("✅ WhatsApp approval notifications sent");
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Could not send approval WhatsApp: " + e.getMessage());
                }

                // Build success response (no payment service — removed for OCI free tier)
                Map<String, Object> response = new HashMap<>();
                response.put("winner", winnerEmail);
                response.put("winningBid", winningAmount);
                response.put("message", "Bidding completed successfully. Item marked as SALE_IN_PROGRESS. Buyer and seller have been notified.");

                return ResponseEntity.ok(response);
            }

            Map<String, Object> error = new HashMap<>();
            error.put("error", "NO_VALID_BIDS");
            error.put("message", "No valid bids found for item");
            return ResponseEntity.status(400).body(error);
        } catch (ResourceNotFoundException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "ITEM_NOT_FOUND");
            error.put("message", "Item not found: " + e.getMessage());
            return ResponseEntity.status(404).body(error);
        } catch (S3Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "S3_CONFIG_ERROR");
            error.put("message", "Failed to access S3 storage. Check AWS_S3_BUCKET_NAME and AWS_DEFAULT_REGION in docker-compose.local.yml");
            error.put("details", e.getMessage());
            System.err.println("❌ S3 Error in completeBidding: " + e.awsErrorDetails());
            return ResponseEntity.status(500).body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "INTERNAL_ERROR");
            error.put("message", "Error completing bidding");
            error.put("details", e.getMessage());
            System.err.println("❌ Unexpected error in completeBidding: " + e);
            e.printStackTrace();
            return ResponseEntity.status(500).body(error);
        }
    }
}

