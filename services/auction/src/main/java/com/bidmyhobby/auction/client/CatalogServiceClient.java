package com.bidmyhobby.auction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "catalog-service")
public interface CatalogServiceClient {
    
    @GetMapping("/api/catalog/item/{itemId}")
    Map<String, Object> getItem(@PathVariable String itemId);
    
    @PutMapping("/api/catalog/items/{itemId}/status")
    Map<String, Object> updateItemStatus(@PathVariable String itemId, @RequestBody Map<String, String> statusUpdate);
}