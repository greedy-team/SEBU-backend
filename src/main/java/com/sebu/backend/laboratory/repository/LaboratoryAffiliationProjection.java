package com.sebu.backend.laboratory.repository;

public interface LaboratoryAffiliationProjection {
    Long getLaboratoryId();
    Long getCollegeId();
    String getCollegeName();
    Long getDepartmentId();
    String getDepartmentName();
}
