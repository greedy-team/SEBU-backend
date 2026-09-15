package com.sebu.backend.department.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AcademicAffiliationQueryIntegrationTest {
    @Autowired AcademicAffiliationResolver resolver;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void currentAndLegacyCollegeGroupsAreLoadedInTwoQueriesForAnyBatchSize() {
        long small = resolveAndCount(List.of("컴퓨터공학과", "무인이동체공학전공"));
        List<String> large = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            large.addAll(List.of("컴퓨터공학과", "국어국문학과", "물리천문학과",
                "무인이동체공학전공", "영어영문학전공", "법학전공", "항공시스템공학전공"));
        }
        assertThat(resolveAndCount(large)).isEqualTo(small).isEqualTo(2L);
    }

    private long resolveAndCount(List<String> names) {
        entityManager.flush();
        entityManager.clear();
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var results = resolver.resolveAll(names);
        assertThat(results.values()).allSatisfy(result -> {
            assertThat(result.college()).isNotNull();
            assertThat(result.college().getCommunityGroup().getDisplayName()).isNotBlank();
        });
        return stats.getPrepareStatementCount();
    }
}
