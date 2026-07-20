package com.bidmyhobby.auction.controller;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuctionControllerTest {

    @Test
    void usesAiEstimateWhenItIsLower() {
        assertEquals(new BigDecimal("500"),
                AuctionController.minimumBid(Map.of("basePrice", 700, "suggestedPrice", 500)));
    }

    @Test
    void usesSellerPriceWhenItIsLower() {
        assertEquals(new BigDecimal("500"),
                AuctionController.minimumBid(Map.of("basePrice", 500, "suggestedPrice", 700)));
    }

    @Test
    void fallsBackToAvailablePrice() {
        assertEquals(new BigDecimal("650"),
                AuctionController.minimumBid(Map.of("basePrice", 650)));
    }
}
