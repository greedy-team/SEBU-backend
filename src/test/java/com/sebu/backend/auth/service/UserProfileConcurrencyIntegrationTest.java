package com.sebu.backend.auth.service;

import com.sebu.backend.auth.port.SejongAuthenticator;
import com.sebu.backend.auth.port.SejongUserProfile;
import com.sebu.backend.auth.repository.RefreshTokenRepository;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.mypage.dto.ProfileUpdateRequest;
import com.sebu.backend.mypage.moderation.IntroductionModerator;
import com.sebu.backend.mypage.moderation.ModerationResult;
import com.sebu.backend.mypage.service.ProfileService;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AuthProvider;
import com.sebu.backend.user.domain.GpaBand;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static com.sebu.backend.support.CookieApiRequests.put;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserProfileConcurrencyIntegrationTest {
    @Autowired AuthService authService;
    @Autowired AuthSessionService authSessionService;
    @Autowired ProfileService profileService;
    @MockitoSpyBean AppUserRepository appUserRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired MockMvc mockMvc;

    @MockitoBean SejongAuthenticator sejongAuthenticator;
    @MockitoBean IntroductionModerator introductionModerator;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        appUserRepository.deleteAll();
        when(introductionModerator.moderate(anyString()))
            .thenReturn(new ModerationResult(true, "v1", "test-provider"));
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void profileUpdateReturnsConflictInsteadOfOverwritingConcurrentSchoolProfile() throws Exception {
        Department initialDepartment = department("동시성초기대학", "동시성초기학과");
        Department changedDepartment = department("동시성변경대학", "동시성변경학과");
        var initialLogin = authSessionService.start(profile(
            "21009990", "기존이름", initialDepartment.getName()
        ));

        CountDownLatch profileLoaded = new CountDownLatch(1);
        CountDownLatch allowProfileUpdate = new CountDownLatch(1);
        when(introductionModerator.moderate("동시성 자기소개")).thenAnswer(invocation -> {
            profileLoaded.countDown();
            if (!allowProfileUpdate.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("PROFILE_UPDATE_NOT_RELEASED");
            }
            return new ModerationResult(true, "v1", "test-provider");
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> profileResult = executor.submit(() -> mockMvc.perform(
                    put("/api/v1/users/me/profile")
                        .with(jwt().jwt(jwt -> jwt
                            .subject(initialLogin.userId().toString())
                            .claim("role", "USER")
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "nickname": "동시닉네임",
                              "grade": 3,
                              "gpaBand": "GTE_3_5",
                              "introduction": "동시성 자기소개"
                            }
                            """)
                ).andReturn());

            assertThat(profileLoaded.await(5, TimeUnit.SECONDS)).isTrue();
            authSessionService.start(profile(
                "21009990", "변경된이름", changedDepartment.getName()
            ));
            allowProfileUpdate.countDown();

            MvcResult conflict = profileResult.get(10, TimeUnit.SECONDS);
            assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
            assertThat(conflict.getResponse().getContentAsString())
                .contains("PROFILE_UPDATE_CONFLICT");
        } finally {
            allowProfileUpdate.countDown();
            executor.shutdownNow();
        }

        AppUser saved = appUserRepository
            .findByProviderAndProviderUserId(AuthProvider.SEJONG, "21009990")
            .orElseThrow();
        assertThat(saved.getName()).isEqualTo("변경된이름");
        assertThat(saved.getSejongDepartmentName()).isEqualTo(changedDepartment.getName());
        assertThat(saved.getMajorDepartment().getId()).isEqualTo(changedDepartment.getId());
        assertThat(saved.getNickname()).isNull();
        assertThat(saved.getGrade()).isNull();
        assertThat(saved.getIntroduction()).isEmpty();
    }

    @Test
    void loginPreservesAProfileCommittedBeforeItAcquiresTheUserLock() throws Exception {
        Department initialDepartment = department("재시도초기대학", "재시도초기학과");
        Department changedDepartment = department("재시도변경대학", "재시도변경학과");
        var initialLogin = authSessionService.start(profile(
            "21009991", "기존이름", initialDepartment.getName()
        ));
        when(sejongAuthenticator.authenticate("21009991", "password"))
            .thenReturn(profile("21009991", "최신이름", changedDepartment.getName()));

        CountDownLatch loginLoaded = new CountDownLatch(1);
        CountDownLatch allowLoginCommit = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        var repositoryDelegate = mockingDetails(appUserRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            if (blockOnce.compareAndSet(true, false)) {
                loginLoaded.countDown();
                if (!allowLoginCommit.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("LOGIN_NOT_RELEASED");
                }
            }
            return repositoryDelegate.answer(invocation);
        }).when(appUserRepository).findByIdForUpdate(initialLogin.userId());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AuthSessionService.LoginSession> loginResult = executor.submit(
                () -> authService.loginWithSejong("21009991", "password")
            );

            assertThat(loginLoaded.await(5, TimeUnit.SECONDS)).isTrue();
            profileService.updateProfile(
                initialLogin.userId(),
                new ProfileUpdateRequest(
                    "보존할닉네임",
                    (short) 3,
                    GpaBand.GTE_3_5,
                    "보존할 자기소개"
                )
            );
            allowLoginCommit.countDown();

            assertThat(loginResult.get(10, TimeUnit.SECONDS).userId())
                .isEqualTo(initialLogin.userId());
        } finally {
            allowLoginCommit.countDown();
            executor.shutdownNow();
        }

        AppUser saved = appUserRepository.findById(initialLogin.userId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("최신이름");
        assertThat(saved.getSejongDepartmentName()).isEqualTo(changedDepartment.getName());
        assertThat(saved.getMajorDepartment().getId()).isEqualTo(changedDepartment.getId());
        assertThat(saved.getNickname()).isEqualTo("보존할닉네임");
        assertThat(saved.getGrade()).isEqualTo((short) 3);
        assertThat(saved.getIntroduction()).isEqualTo("보존할 자기소개");
        assertThat(saved.isProfileCompleted()).isTrue();
        assertThat(refreshTokenRepository.countByUser_Id(saved.getId())).isEqualTo(2);
    }

    private Department department(String collegeName, String departmentName) {
        College college = collegeRepository.save(new College(collegeName));
        return departmentRepository.save(new Department(college, departmentName));
    }

    private SejongUserProfile profile(String studentId, String name, String departmentName) {
        return new SejongUserProfile(studentId, name, departmentName);
    }
}
