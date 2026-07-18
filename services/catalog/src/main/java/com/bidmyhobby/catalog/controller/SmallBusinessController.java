package com.bidmyhobby.catalog.controller;

import com.bidmyhobby.catalog.model.SmallBusiness;
import com.bidmyhobby.catalog.service.SmallBusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/small-businesses")
@CrossOrigin(origins = "*")
public class SmallBusinessController {

    @Autowired
    private SmallBusinessService smallBusinessService;

    @PostMapping("/upload-business-card")
    public ResponseEntity<Map<String, Object>> uploadBusinessCard(
            @RequestParam("businessCard") MultipartFile businessCardImage,
            @RequestParam("uploaderEmail") String uploaderEmail,
            @RequestParam("businessName") String businessName,
            @RequestParam("category") String category,
            @RequestParam("location") String location,
            @RequestParam(value = "description", required = false) String description) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (businessCardImage.isEmpty()) {
                response.put("error", "Business card image is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            SmallBusiness business = smallBusinessService.createBusinessFromCard(
                    businessCardImage, uploaderEmail, businessName, category, location, description);
            
            response.put("success", true);
            response.put("message", "Business card processed successfully");
            response.put("business", business);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("error", "Failed to process business card: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping
    public ResponseEntity<Page<SmallBusiness>> getAllBusinesses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<SmallBusiness> businesses = smallBusinessService.getAllApprovedBusinesses(pageable);
        return ResponseEntity.ok(businesses);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<Page<SmallBusiness>> getBusinessesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SmallBusiness> businesses = smallBusinessService.getBusinessesByCategory(category, pageable);
        return ResponseEntity.ok(businesses);
    }

    @GetMapping("/location/{location}")
    public ResponseEntity<Page<SmallBusiness>> getBusinessesByLocation(
            @PathVariable String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SmallBusiness> businesses = smallBusinessService.getBusinessesByLocation(location, pageable);
        return ResponseEntity.ok(businesses);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<SmallBusiness>> searchBusinesses(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<SmallBusiness> businesses = smallBusinessService.searchBusinesses(keyword, pageable);
        return ResponseEntity.ok(businesses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SmallBusiness> getBusinessById(@PathVariable Long id) {
        Optional<SmallBusiness> business = smallBusinessService.getBusinessById(id);
        return business.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my-businesses")
    public ResponseEntity<List<SmallBusiness>> getMyBusinesses(@RequestParam String email) {
        List<SmallBusiness> businesses = smallBusinessService.getBusinessesByUploader(email);
        return ResponseEntity.ok(businesses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SmallBusiness> updateBusiness(
            @PathVariable Long id,
            @RequestBody SmallBusiness updatedBusiness) {
        
        try {
            SmallBusiness business = smallBusinessService.updateBusiness(id, updatedBusiness);
            return ResponseEntity.ok(business);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteBusiness(@PathVariable Long id) {
        try {
            smallBusinessService.deleteBusiness(id);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Business deleted successfully");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getBusinessStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBusinesses", smallBusinessService.getTotalBusinessCount());
        return ResponseEntity.ok(stats);
    }
}