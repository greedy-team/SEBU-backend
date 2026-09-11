package com.sebu.backend.auth.service;

import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SejongProfileLoginIntegrationTest {
    @Autowired AuthSessionService authSessionService;
    @Autowired AppUserRepository appUserRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired EntityManager entityManager;

    @Test
    void createsOnceUpdatesOnlyChangedSchoolFieldsAndPreservesGrade() {
        SejongUserProfile initial = profile("홍길동", "컴퓨터공학과");
        var first = (AuthSessionService.LoginSession) authSessionService.login(initial);
        var user = appUserRepository.findByProviderAndProviderUserId(AuthProvider.SEJONG, "21012345")
            .orElseThrow();

        assertThat(first.isNewUser()).isTrue();
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getSejongDepartmentName()).isEqualTo("컴퓨터공학과");
        assertThat(user.getMajorDepartment()).isNotNull();
        assertThat(user.getMajorDepartment().getName()).isEqualTo("컴퓨터공학과");
        assertThat(user.getGrade()).isNull();
        assertThat(user.isProfileCompleted()).isFalse();

        LocalDateTime initialProfileUpdatedAt = user.getProfileUpdatedAt();
        var second = (AuthSessionService.LoginSession) authSessionService.login(initial);
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.isNewUser()).isFalse();
        assertThat(user.getProfileUpdatedAt()).isEqualTo(initialProfileUpdatedAt);

        user.updateGrade(3, initialProfileUpdatedAt.plusMinutes(1));
        authSessionService.login(profile("홍길순", "컴퓨터공학과"));
        authSessionService.login(profile("홍길순", "컴퓨터공학과(개편)"));
        entityManager.flush();
        entityManager.clear();

        var updated = appUserRepository.findById(first.userId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("홍길순");
        assertThat(updated.getSejongDepartmentName()).isEqualTo("컴퓨터공학과(개편)");
        assertThat(updated.getMajorDepartment()).isNull();
        assertThat(updated.getGrade()).isEqualTo((short) 3);
        assertThat(updated.isProfileCompleted()).isTrue();
        assertThat(appUserRepository.count()).isOne();
        assertThat(refreshTokenRepository.countByUser_Id(first.userId())).isEqualTo(4);
    }

    @Test
    void schoolDepartmentNameChangeDoesNotBlockLogin() {
        var first = (AuthSessionService.LoginSession) authSessionService.login(new SejongUserProfile(
            "anonymous-student", "테스트사용자", "무인이동체공학전공/지능기전공학부"
        ));
        var second = (AuthSessionService.LoginSession) authSessionService.login(new SejongUserProfile(
            "anonymous-student", "테스트사용자", "무인이동체공학전공"
        ));

        var user = appUserRepository.findById(first.userId()).orElseThrow();
        assertThat(first.isNewUser()).isTrue();
        assertThat(second.isNewUser()).isFalse();
        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(user.getSejongDepartmentName()).isEqualTo("무인이동체공학전공");
        assertThat(user.getMajorDepartment()).isNull();
        assertThat(user.getGrade()).isNull();
        assertThat(user.isProfileCompleted()).isFalse();
        assertThat(appUserRepository.count()).isOne();
    }

    private SejongUserProfile profile(String name, String department) {
        return new SejongUserProfile("21012345", name, department);
    }
}
