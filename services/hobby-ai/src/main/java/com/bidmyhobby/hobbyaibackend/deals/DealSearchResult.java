package com.bidmyhobby.hobbyaibackend.deals;

import java.util.List;

public record DealSearchResult(
        String query,
        String summary,
        String proTip,
        List<AiFoundDeal> deals,
        boolean aiGenerated
) {}
