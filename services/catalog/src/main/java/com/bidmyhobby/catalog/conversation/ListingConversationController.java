package com.bidmyhobby.catalog.conversation;

import com.bidmyhobby.catalog.auth.AuthenticatedUser;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/catalog/listing-conversations")
public class ListingConversationController {
    private final ListingConversationService service;
    private final AuthenticatedUser auth;
    public ListingConversationController(ListingConversationService service, AuthenticatedUser auth) { this.service = service; this.auth = auth; }

    @PostMapping
    public ListingConversationResponse create(@RequestBody StartRequest request, @CookieValue("bmh_session") String token) {
        return service.create(auth.require(token).email());
    }
    @GetMapping("/{id}")
    public ListingConversationResponse get(@PathVariable String id, @RequestParam(required = false) String ownerId,
            @CookieValue("bmh_session") String token) {
        return service.get(id, auth.require(token).email());
    }
    @PostMapping("/{id}/messages")
    public ListingConversationResponse message(@PathVariable String id, @RequestParam String ownerId,
            @RequestBody MessageRequest request, @CookieValue("bmh_session") String token) {
        return service.message(id, auth.require(token).email(), request.message());
    }
    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ListingConversationResponse images(@PathVariable String id, @RequestParam String ownerId,
            @RequestParam("files") List<MultipartFile> files, @CookieValue("bmh_session") String token) throws Exception {
        return service.addImages(id, auth.require(token).email(), files);
    }
    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable String id, @PathVariable String imageId, @RequestParam(required = false) String ownerId,
            @CookieValue("bmh_session") String token) {
        service.deleteImage(id, auth.require(token).email(), imageId);
    }
    @PatchMapping("/{id}/draft")
    public ListingConversationResponse patch(@PathVariable String id, @RequestParam String ownerId,
            @RequestBody Map<String, Object> patch, @CookieValue("bmh_session") String token) {
        return service.patchDraft(id, auth.require(token).email(), patch);
    }
    @PostMapping("/{id}/publish")
    public ListingConversationResponse publish(@PathVariable String id, @RequestParam(required = false) String ownerId,
            @CookieValue("bmh_session") String token) throws Exception {
        return service.publish(id, auth.require(token).email());
    }
    public record StartRequest(String ownerId) {}
    public record MessageRequest(String message) {}
}
