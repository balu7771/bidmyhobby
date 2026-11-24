package com.bidmyhobby.hobbyaibackend.deals;

import java.util.List;

public record AiFoundDeal(
        String name,
        String store,
        String category,
        String tagline,
        double usRegularPrice,
        double usSalePrice,
        double usSavings,
        int discountPercent,
        int indiaPriceInr,
        int savingsVsIndiaInr,
        String badge,
        List<String> keyHighlights,
        String suggestedSearchUrl,
        String holidayShoppingTip
) {}
