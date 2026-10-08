package com.sebu.backend.researchfield.promotion.service;

import com.sebu.backend.researchfield.domain.ResearchField;

record ResearchFieldPromotionTarget(
    ResearchField researchField,
    boolean created
) {
}
