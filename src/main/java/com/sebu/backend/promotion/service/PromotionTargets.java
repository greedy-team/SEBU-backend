package com.sebu.backend.promotion.service;

import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.professor.domain.Professor;

record PromotionTargets(
    Professor professor,
    Laboratory laboratory,
    boolean canonicalCreated,
    boolean canonicalUpdated
) {
}
