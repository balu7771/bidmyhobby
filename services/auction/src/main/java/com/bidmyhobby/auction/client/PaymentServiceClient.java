package com.bidmyhobby.auction.client;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String paymentServiceUrl = "http://localhost:8080/api/payments";

    public Map<String, Object> createOrderForItem(String itemId, String itemName, String buyerEmail, String sellerEmail, Double bidAmount) {
        try {
            System.out.println("💳 Creating payment order for winning bid");
            
            Map<String, Object> request = new HashMap<>();
            request.put("itemId", itemId);
            request.put("itemName", itemName);
            request.put("buyerEmail", buyerEmail);
            request.put("sellerEmail", sellerEmail);
            request.put("amount", bidAmount); // Send in INR, let payment service convert

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                paymentServiceUrl + "/create-order-for-item", 
                entity, 
                Map.class
            );

            System.out.println("✅ Payment order created successfully");
            return response;

        } catch (Exception e) {
            System.err.println("❌ Failed to create payment order: " + e.getMessage());
            throw new RuntimeException("Payment order creation failed", e);
        }
    }
}