package com.bidmyhobby.auction.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Bid {
    private String bidId;
    private String itemId;
    private String bidderEmail;
    private BigDecimal bidAmount;
    private String currency = "INR";
    private LocalDateTime timestamp;
    private String status = "ACTIVE";

    public Bid() {
        this.timestamp = LocalDateTime.now();
    }

    public String getBidId() {
        return bidId;
    }

    public void setBidId(String bidId) {
        this.bidId = bidId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getBidderEmail() {
        return bidderEmail;
    }

    public void setBidderEmail(String bidderEmail) {
        this.bidderEmail = bidderEmail;
    }

    public BigDecimal getBidAmount() {
        return bidAmount;
    }

    public void setBidAmount(BigDecimal bidAmount) {
        this.bidAmount = bidAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}