package com.sebu.backend.researchfield.promotion.service;

record ResearchFieldPromotionOutcome(
    boolean fieldCreated,
    boolean linkCreated,
    boolean promotionRecorded,
    boolean skipped
) {
    static ResearchFieldPromotionOutcome skippedOutcome() {
        return new ResearchFieldPromotionOutcome(false, false, false, true);
    }

    static ResearchFieldPromotionOutcome completed(
        boolean fieldCreated,
        boolean linkCreated,
        boolean promotionRecorded
    ) {
        boolean skipped = !fieldCreated && !linkCreated && !promotionRecorded;
        return new ResearchFieldPromotionOutcome(
            fieldCreated,
            linkCreated,
            promotionRecorded,
            skipped
        );
    }
}
