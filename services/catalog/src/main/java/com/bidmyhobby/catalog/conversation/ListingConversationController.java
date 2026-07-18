package com.bidmyhobby.catalog.conversation;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/catalog/listing-conversations")
public class ListingConversationController {
    private final ListingConversationService service;
    public ListingConversationController(ListingConversationService service) { this.service = service; }

    @PostMapping
    public ListingConversationResponse create(@RequestBody StartRequest request) {
        return service.create(request.ownerId());
    }
    @GetMapping("/{id}")
    public ListingConversationResponse get(@PathVariable String id, @RequestParam String ownerId) {
        return service.get(id, ownerId);
    }
    @PostMapping("/{id}/messages")
    public ListingConversationResponse message(@PathVariable String id, @RequestParam String ownerId,
            @RequestBody MessageRequest request) {
        return service.message(id, ownerId, request.message());
    }
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ListingConversationResponse images(@PathVariable String id, @RequestParam String ownerId,
            @RequestParam("files") List<MultipartFile> files) throws Exception {
        return service.addImages(id, ownerId, files);
    }
    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable String id, @PathVariable String imageId, @RequestParam String ownerId) {
        service.deleteImage(id, ownerId, imageId);
    }
    @PatchMapping("/{id}/draft")
    public ListingConversationResponse patch(@PathVariable String id, @RequestParam String ownerId,
            @RequestBody Map<String, Object> patch) {
        return service.patchDraft(id, ownerId, patch);
    }
    @PostMapping("/{id}/publish")
    public ListingConversationResponse publish(@PathVariable String id, @RequestParam String ownerId) throws Exception {
        return service.publish(id, ownerId);
    }
    public record StartRequest(String ownerId) {}
    public record MessageRequest(String message) {}
}
