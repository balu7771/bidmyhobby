package com.bidmyhobby.hobbyaibackend.notifications;

import com.bidmyhobby.hobbyaibackend.auth.EmailSender;
import org.springframework.stereotype.Service;

@Service
public class AuctionEmailNotificationService {
    private final EmailSender emailSender;

    public AuctionEmailNotificationService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void bidPlaced(String creatorEmail, String bidderEmail, String itemName,
            String amount, String currency) {
        String value = money(amount, currency);
        String creatorContent =
                """
                Hello,

                A hobbyist has placed a bid of %s on your creation, "%s".

                Sign in to BidMyHobby to review the bid. The bidder's contact details remain private until you approve a winning bid.

                A bid is an expression of interest. You are free to review it without any obligation to accept.

                — BidMyHobby
                """.formatted(value, itemName);

        String bidderContent =
                """
                Hello,

                We received your bid of %s on "%s".

                The creator has been notified and may review or approve a bid at their discretion. We will introduce you by email only if the creator approves your bid.

                Placing a bid does not require either party to complete an exchange.

                — BidMyHobby
                """.formatted(value, itemName);
        sendBoth(creatorEmail, "New bid on " + itemName, creatorContent,
                bidderEmail, "Your bid on " + itemName + " was received", bidderContent);
    }

    public void bidApproved(String creatorEmail, String bidderEmail, String itemName,
            String amount, String currency) {
        String value = money(amount, currency);
        String shared = """
                Great news — the creator has approved the bid of %s for "%s".

                Creator: %s
                Selected bidder: %s

                You may now contact each other directly to discuss the item's condition, collection or delivery, payment, timing, and any other exchange details.

                This introduction is based on a shared appreciation for the hobby and the value you have agreed to explore. It is a no-obligation introduction, not a legally binding sale or contract created by BidMyHobby. Either party may decide not to proceed. Please verify the item and terms, communicate respectfully, and use a safe payment and handover method.

                BidMyHobby does not handle payment, shipping, inspection, or guarantees between the parties.

                — BidMyHobby
                """.formatted(value, itemName, creatorEmail, bidderEmail);

        sendBoth(creatorEmail, "You approved a bid for " + itemName, shared,
                bidderEmail, "Your bid for " + itemName + " was approved", shared);
    }

    private void sendBoth(String firstEmail, String firstSubject, String firstContent,
            String secondEmail, String secondSubject, String secondContent) {
        RuntimeException failure = null;
        try {
            emailSender.sendMessage(firstEmail, firstSubject, firstContent);
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            emailSender.sendMessage(secondEmail, secondSubject, secondContent);
        } catch (RuntimeException e) {
            if (failure == null) failure = e;
            else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
    }

    private String money(String amount, String currency) {
        String code = currency == null || currency.isBlank() ? "INR" : currency.toUpperCase();
        String symbol = switch (code) {
            case "INR" -> "₹";
            case "GBP" -> "£";
            case "USD" -> "$";
            default -> code + " ";
        };
        return symbol + amount;
    }
}
