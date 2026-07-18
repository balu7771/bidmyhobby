package com.bidmyhobby.auction.event;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

@Service
public class BidEventPublisher {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String notificationServiceUrl = "http://notification-service/api/notifications/bid-placed";

    public void publishBidEvent(String itemId, String bidderEmail, Double bidAmount, String itemOwnerEmail, String itemName) {
        try {
            // Send bid notification to item owner
            Map<String, Object> ownerNotification = new HashMap<>();
            ownerNotification.put("itemOwnerEmail", itemOwnerEmail);
            ownerNotification.put("itemName", itemName);
            ownerNotification.put("bidAmount", bidAmount);
            ownerNotification.put("bidderEmail", bidderEmail);
            ownerNotification.put("itemId", itemId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(ownerNotification, headers);

            restTemplate.postForObject(notificationServiceUrl, request, String.class);
            
            // TODO: Send outbid notifications to previous bidders
            // This would require getting previous bids from BidStorageService
            
        } catch (Exception e) {
            System.err.println("Failed to send notification: " + e.getMessage());
        }
    }
    
    public void publishItemUploadEvent(String itemId, String ownerEmail, String itemName) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("ownerEmail", ownerEmail);
            notification.put("itemName", itemName);
            notification.put("itemId", itemId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(notification, headers);

            String uploadNotificationUrl = "http://notification-service/api/notifications/item-uploaded";
            restTemplate.postForObject(uploadNotificationUrl, request, String.class);
        } catch (Exception e) {
            System.err.println("Failed to send upload notification: " + e.getMessage());
        }
    }
}