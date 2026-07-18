package com.bidmyhobby.catalog.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationPublisher {

    private final RestTemplate restTemplate = new RestTemplate();
    @Value("${notification.service.url}")
    private String notificationServiceUrl;
    
    private String getUploadNotificationUrl() {
        return notificationServiceUrl + "/item-uploaded";
    }
    
    private String getSoldNotificationUrl() {
        return notificationServiceUrl + "/item-sold";
    }

    private String getCustomEmailUrl() {
        return notificationServiceUrl + "/custom-email";
    }

    public void publishItemUploadEvent(String itemId, String ownerEmail, String itemName) {
        try {
            System.out.println("📧 [SKIPPED] Sending upload notification for: " + itemName + " to " + ownerEmail);
            // Notification service is disabled. Moving to WhatsApp flow.
        } catch (Exception e) {
            System.err.println("❌ Error in publishItemUploadEvent: " + e.getMessage());
        }
    }

    public void publishItemSoldEvent(String itemId, String ownerEmail, String itemName) {
        try {
            System.out.println("📧 [SKIPPED] Sending sold notification for: " + itemName + " to " + ownerEmail);
            // Notification service is disabled. Moving to WhatsApp flow.
        } catch (Exception e) {
            System.err.println("❌ Error in publishItemSoldEvent: " + e.getMessage());
        }
    }

    public void publishItemRejectedEvent(String itemId, String ownerEmail, String itemName, String reason) {
        try {
            System.out.println("📧 [SKIPPED] Sending rejection notification for: " + itemName + " to " + ownerEmail);
            // Notification service is disabled. Moving to WhatsApp flow.
        } catch (Exception e) {
            System.err.println("❌ Error in publishItemRejectedEvent: " + e.getMessage());
        }
    }
}