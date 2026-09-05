package com.bidmyhobby.catalog.conversation;

import com.bidmyhobby.catalog.model.Item;
import com.bidmyhobby.catalog.service.CatalogService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Service
public class ListingConversationService {
    private static final int MAX_IMAGES = 4;
    private static final long MAX_IMAGE_BYTES = 13L * 1024 * 1024;
    private static final double AUTO_PUBLISH_CONFIDENCE = 0.85;

    private final ListingConversationRepository conversations;
    private final ListingDraftImageRepository images;
    private final OpenAiListingService openAi;
    private final CatalogService catalog;
    private final ObjectMapper mapper;

    public ListingConversationService(ListingConversationRepository conversations,
            ListingDraftImageRepository images, OpenAiListingService openAi,
            CatalogService catalog, ObjectMapper mapper) {
        this.conversations = conversations;
        this.images = images;
        this.openAi = openAi;
        this.catalog = catalog;
        this.mapper = mapper;
    }

    @Transactional
    public ListingConversationResponse create(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
        ListingConversation c = conversations.save(new ListingConversation(ownerId.trim()));
        append(c, "assistant", "Add one to four photos and tell me about the hobby item you want to sell.");
        return response(conversations.save(c), null);
    }

    @Transactional(readOnly = true)
    public ListingConversationResponse get(String id, String ownerId) {
        return response(owned(id, ownerId), null);
    }

    public ListingConversationResponse addImages(String id, String ownerId, List<MultipartFile> files) throws Exception {
        ListingConversation c = owned(id, ownerId);
        if (terminal(c)) throw new IllegalStateException("This conversation can no longer be changed");
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("At least one image is required");
        if (images.countByConversationId(id) + files.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Maximum four images are allowed");
        }
        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("Each image must be non-empty and no larger than 13 MB");
            }
            String type = file.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw new IllegalArgumentException("Only image files are supported");
            }
            images.save(new ListingDraftImage(id, safeFilename(file.getOriginalFilename()), type, file.getBytes()));
        }
        append(c, "user", "[Added " + files.size() + " image" + (files.size() == 1 ? "" : "s") + "]");
        c.setState("COLLECTING");
        c = conversations.save(c);
        return analyze(c);
    }

    public ListingConversationResponse message(String id, String ownerId, String message) {
        ListingConversation c = owned(id, ownerId);
        if (terminal(c)) throw new IllegalStateException("This conversation can no longer be changed");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
        String userMessage = message.trim();
        Optional<ParsedPrice> statedPrice = extractSellerAskingPrice(userMessage);
        if (statedPrice.isPresent()) {
            c.setBasePrice(statedPrice.get().amount());
            c.setPriceConfirmed(true);
            if (statedPrice.get().currency() != null) {
                c.setCurrency(statedPrice.get().currency());
                c.setCurrencyConfirmed(true);
            }
        }
        inferListingType(userMessage).ifPresent(c::setListingType);
        append(c, "user", userMessage);
        c = conversations.save(c);
        if (images.countByConversationId(id) == 0) {
            String reply = "Please add at least one clear photo so I can identify and moderate the item.";
            append(c, "assistant", reply);
            return response(conversations.save(c), reply);
        }
        return analyze(c);
    }

    @Transactional
    public ListingConversationResponse patchDraft(String id, String ownerId, Map<String, Object> patch) {
        ListingConversation c = owned(id, ownerId);
        if (patch.containsKey("title")) c.setTitle(clean(patch.get("title")));
        if (patch.containsKey("description")) c.setDescription(maskContact(clean(patch.get("description"))));
        if (patch.containsKey("city")) c.setCity(clean(patch.get("city")));
        if (patch.containsKey("size")) c.setSize(clean(patch.get("size")));
        if (patch.containsKey("basePrice")) c.setBasePrice(asDouble(patch.get("basePrice")));
        if (patch.containsKey("currency")) { c.setCurrency(clean(patch.get("currency"))); c.setCurrencyConfirmed(true); }
        if (patch.containsKey("listingType")) c.setListingType(normalizeListingType(clean(patch.get("listingType"))));
        if (Boolean.TRUE.equals(patch.get("confirmSuggestedPrice"))) {
            c.setBasePrice(c.getSuggestedPrice());
            c.setPriceConfirmed(true);
        }
        if (patch.containsKey("basePrice")) c.setPriceConfirmed(true);
        c.setState(ready(c) ? "READY_FOR_REVIEW" : "NEEDS_INFORMATION");
        append(c, "assistant", ready(c)
                ? "Your draft is ready. Review it and choose Publish."
                : "I saved those changes. Complete the remaining details before publishing.");
        return response(conversations.save(c), null);
    }

    public ListingConversationResponse publish(String id, String ownerId) throws Exception {
        ListingConversation c = owned(id, ownerId);
        if (c.getPublishedItemId() != null) return response(c, "This listing has already been published.");
        if (!c.isSafe() || !c.isHobbyItem()) throw new IllegalStateException("Only approved hobby items can be published");
        if (!ready(c)) throw new IllegalStateException("Review and confirm all required listing details first");
        c.setState("PUBLISHING");
        c = conversations.saveAndFlush(c);
        boolean manualReview = c.getConfidence() < AUTO_PUBLISH_CONFIDENCE;
        Item item = catalog.publishConversationDraft(c, images.findByConversationIdOrderByCreatedAtAsc(id), manualReview);
        c = owned(id, ownerId);
        c.setPublishedItemId(item.getItemId());
        c.setState(manualReview ? "REVIEW_REQUIRED" : "PUBLISHED");
        append(c, "assistant", manualReview
                ? "Your listing was submitted for review because the classification confidence was low."
                : "Your hobby item is now published.");
        return response(conversations.save(c), null);
    }

    @Transactional
    public void deleteImage(String id, String ownerId, String imageId) {
        owned(id, ownerId);
        ListingDraftImage image = images.findById(imageId)
                .filter(i -> id.equals(i.getConversationId()))
                .orElseThrow(() -> new NoSuchElementException("Image not found"));
        images.delete(image);
    }

    private ListingConversationResponse analyze(ListingConversation c) {
        c.setState("ANALYZING");
        c = conversations.saveAndFlush(c);
        OpenAiListingService.ListingAnalysis a =
                openAi.analyze(c.getTranscriptJson(), images.findByConversationIdOrderByCreatedAtAsc(c.getId()));
        c = conversations.findById(c.getId())
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));
        c.setHobbyItem(a.hobbyItem());
        c.setSafe(a.safe());
        c.setConfidence(a.confidence());
        c.setModerationReason(a.moderationReason());
        if (!a.hobbyItem() || !a.safe()) {
            c.setState("REJECTED");
            append(c, "assistant", a.moderationReason());
            return response(conversations.save(c), a.moderationReason());
        }
        c.setTitle(a.title());
        c.setDescription(maskContact(a.description()));
        c.setCategory(a.category());
        c.setConditionLabel(a.condition());
        c.setListingType(inferListingType(c.getTranscriptJson())
                .orElseGet(() -> normalizeListingType(a.listingType())));
        if (!c.isCurrencyConfirmed() && notBlank(a.currency())) {
            String detectedCurrency = a.currency().toUpperCase();
            if (!detectedCurrency.equals(c.getCurrency())) {
                // The valuation below is stale once its currency changes; re-baseline it.
                c.setSuggestedPrice(null);
            }
            c.setCurrency(detectedCurrency);
        }
        // Retain the first independent AI valuation. Seller messages must never rewrite it.
        if (c.getSuggestedPrice() == null) {
            c.setSuggestedPrice(a.suggestedPrice());
        }
        if (a.sellerAskingPrice() != null && !c.isPriceConfirmed() && isAuction(c)) {
            c.setBasePrice(a.sellerAskingPrice());
            c.setPriceConfirmed(true);
        }
        if (notBlank(a.city())) c.setCity(a.city());
        if (notBlank(a.size())) c.setSize(a.size());
        c.setState(a.readyForReview() && ready(c) ? "READY_FOR_REVIEW" : "NEEDS_INFORMATION");
        append(c, "assistant", a.assistantMessage());
        return response(conversations.save(c), a.assistantMessage());
    }

    private ListingConversation owned(String id, String ownerId) {
        ListingConversation c = conversations.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));
        if (ownerId == null || !c.getOwnerId().equals(ownerId)) throw new SecurityException("Not your conversation");
        return c;
    }

    private ListingConversationResponse response(ListingConversation c, String assistantMessage) {
        List<String> missing = new ArrayList<>();
        if (!notBlank(c.getTitle())) missing.add("title");
        if (!notBlank(c.getDescription())) missing.add("description");
        if (!notBlank(c.getCity())) missing.add("city");
        if (isAuction(c) && (!c.isPriceConfirmed() || c.getBasePrice() == null)) missing.add("askingPrice");
        List<ListingConversationResponse.ImageSummary> imageList =
                images.findByConversationIdOrderByCreatedAtAsc(c.getId()).stream()
                        .map(i -> new ListingConversationResponse.ImageSummary(i.getId(), i.getFilename(), i.getContentType()))
                        .toList();
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("title", c.getTitle());
        draft.put("description", c.getDescription());
        draft.put("category", c.getCategory());
        draft.put("condition", c.getConditionLabel());
        draft.put("suggestedPrice", c.getSuggestedPrice());
        draft.put("basePrice", c.getBasePrice());
        draft.put("currency", c.getCurrency());
        draft.put("listingType", c.getListingType());
        draft.put("city", c.getCity());
        draft.put("size", c.getSize());
        draft.put("confidence", c.getConfidence());
        draft.put("moderationReason", c.getModerationReason());
        try {
            return new ListingConversationResponse(c.getId(), c.getState(), c.getVersion(), assistantMessage,
                    mapper.readTree(c.getTranscriptJson()), draft, missing, imageList,
                    actions(c, missing), c.getPublishedItemId());
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private List<String> actions(ListingConversation c, List<String> missing) {
        if (terminal(c)) return List.of();
        List<String> actions = new ArrayList<>(List.of("SEND_MESSAGE", "ADD_IMAGE", "EDIT_DRAFT", "CANCEL"));
        if (missing.isEmpty() && c.isSafe() && c.isHobbyItem()) actions.add("PUBLISH");
        return actions;
    }

    private void append(ListingConversation c, String role, String text) {
        try {
            ArrayNode transcript = (ArrayNode) mapper.readTree(c.getTranscriptJson());
            ObjectNode message = mapper.createObjectNode();
            message.put("role", role);
            message.put("text", text);
            transcript.add(message);
            c.setTranscriptJson(mapper.writeValueAsString(transcript));
        } catch (Exception e) { throw new IllegalStateException("Could not update conversation", e); }
    }

    private boolean ready(ListingConversation c) {
        return notBlank(c.getTitle()) && notBlank(c.getDescription()) && notBlank(c.getCity())
                && (!isAuction(c) || (c.getBasePrice() != null && c.getBasePrice() >= 0 && c.isPriceConfirmed()));
    }
    private boolean terminal(ListingConversation c) {
        return Set.of("PUBLISHED", "REVIEW_REQUIRED", "REJECTED", "CANCELLED").contains(c.getState());
    }
    private boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private String clean(Object value) { return value == null ? null : value.toString().trim(); }
    private Double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return Double.valueOf(value.toString());
    }
    private static final Map<String, String> CURRENCY_SYMBOLS = Map.of(
            "₹", "INR", "rs", "INR", "inr", "INR",
            "$", "USD", "usd", "USD",
            "£", "GBP", "gbp", "GBP",
            "€", "EUR", "eur", "EUR");

    private record ParsedPrice(double amount, String currency) {}

    private Optional<ParsedPrice> extractSellerAskingPrice(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?i)(?:my\\s+asking\\s+price|asking\\s+price|asking|i\\s+want|price\\s+is|sell(?:\\s+it)?\\s+for)"
                        + "\\s*(?:is|of|:|-)?\\s*(₹|rs\\.?|inr|\\$|usd|£|gbp|€|eur)?\\s*([0-9][0-9,]*(?:\\.\\d{1,2})?)")
                .matcher(message);
        if (!matcher.find()) return Optional.empty();
        try {
            double price = Double.parseDouble(matcher.group(2).replace(",", ""));
            if (price < 0) return Optional.empty();
            String symbol = matcher.group(1);
            String currency = symbol == null ? null
                    : CURRENCY_SYMBOLS.get(symbol.toLowerCase().replace(".", ""));
            return Optional.of(new ParsedPrice(price, currency));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
    private Optional<String> inferListingType(String message) {
        if (message.matches("(?is).*(not\\s+for\\s+(sale|bidding)|display\\s+only|view\\s+only|showcase\\s+only|do\\s+not\\s+(sell|auction)).*")) {
            return Optional.of("DISPLAY_ONLY");
        }
        if (message.matches("(?is).*(for\\s+(sale|auction|bidding)|accept\\s+bids|sell\\s+it).*")) {
            return Optional.of("AUCTION");
        }
        return Optional.empty();
    }
    private String normalizeListingType(String value) {
        return "DISPLAY_ONLY".equalsIgnoreCase(value) ? "DISPLAY_ONLY" : "AUCTION";
    }
    private boolean isAuction(ListingConversation c) {
        return !"DISPLAY_ONLY".equals(c.getListingType());
    }
    private String safeFilename(String filename) {
        return filename == null ? "image.jpg" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    private String maskContact(String value) {
        if (value == null) return null;
        String masked = value.replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "[email hidden]");
        return masked.replaceAll("(?<!\\d)(?:\\+?\\d[\\d .()-]{6,}\\d)(?!\\d)", "[phone hidden]");
    }
}
