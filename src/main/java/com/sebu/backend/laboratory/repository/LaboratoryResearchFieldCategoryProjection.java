package com.sebu.backend.laboratory.repository;

public interface LaboratoryResearchFieldCategoryProjection {
    Long getLaboratoryId();

    Long getResearchFieldId();

    Long getCategoryId();

    String getCategoryCode();

    String getCategoryName();

    Long getParentId();

    Integer getDisplayOrder();
}
