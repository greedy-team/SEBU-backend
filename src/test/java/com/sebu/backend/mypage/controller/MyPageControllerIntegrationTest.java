package com.sebu.backend.mypage.controller;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.mypage.moderation.IntroductionModerationUnavailableException;
import com.sebu.backend.mypage.moderation.IntroductionModerator;
import com.sebu.backend.mypage.moderation.ModerationResult;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static com.sebu.backend.support.CookieApiRequests.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static com.sebu.backend.support.CookieApiRequests.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class MyPageControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository appUserRepository;
    @Autowired
    CollegeRepository collegeRepository;
    @Autowired
    DepartmentRepository departmentRepository;
    @MockitoBean
    IntroductionModerator introductionModerator;

    @BeforeEach
    void setUp() {
        when(introductionModerator.moderate(anyString()))
                .thenReturn(
                        new ModerationResult(
                                true,
                                "v1",
                                "test-provider"
                        )
                );
    }


    @Test
    void 로그인한_사용자는_마이페이지를_조회할_수_있다() throws Exception {
        AppUser user = appUserRepository.save(
                new AppUser("student@example.com")
        );

        mockMvc.perform(
                        get("/api/v1/users/me/mypage")
                                .with(jwt().jwt(jwt ->
                                        jwt.subject(user.getId().toString())
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        "private, no-store"
                ))
                .andExpect(jsonPath("$.data.profile").exists())
                .andExpect(jsonPath("$.data.summary.bookmarkedLaboratoryCount")
                        .value(0))
                .andExpect(jsonPath("$.data.bookmarkedLaboratories.items")
                        .isArray())
                .andExpect(jsonPath("$.data.bookmarkedLaboratories.items")
                        .isEmpty())
                .andExpect(jsonPath("$.data.bookmarkedLaboratories.hasNext")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.summary.bookmarkedPostCount")
                        .value(0))
                .andExpect(jsonPath("$.data.bookmarkedPosts.items")
                        .isArray())
                .andExpect(jsonPath("$.data.bookmarkedPosts.items")
                        .isEmpty());
    }

    @Test
    void 로그인한_사용자는_프로필을_저장할_수_있다() throws Exception {
        College college = collegeRepository.save(
                new College("프로필컨트롤러대학")
        );

        Department major = departmentRepository.save(
                new Department(college, "AI로봇학과")
        );
        AppUser user = sejongUser("profile-controller", "홍길동", major);

        String requestBody = """
                {
                  "nickname": "길동이",
                  "grade": 3,
                  "academicField": "ENGINEERING",
                  "gpaBand": "GTE_3_5",
                  "introduction": "머신러닝에 관심이 있습니다."
                }
                """;

        mockMvc.perform(
                        put("/api/v1/users/me/profile")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        "private, no-store"
                ))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.nickname").value("길동이"))
                .andExpect(jsonPath("$.data.grade").value(3))
                .andExpect(jsonPath("$.data.department.id")
                        .value(major.getId().toString()))
                .andExpect(jsonPath("$.data.department.name")
                        .value("AI로봇학과"))
                .andExpect(jsonPath("$.data.gpaBand")
                        .value("GTE_3_5"))
                .andExpect(jsonPath("$.data.introduction")
                        .value("머신러닝에 관심이 있습니다."))
                .andExpect(jsonPath("$.data.profileCompleted")
                        .value(true))
                .andExpect(jsonPath("$.data.profileUpdatedAt").exists());
    }

    @Test
    void 닉네임은_nfkc와_공백을_정규화해_저장한다() throws Exception {
        AppUser user = appUserRepository.save(new AppUser("normalized-nickname@example.com"));

        mockMvc.perform(
                        put("/api/v1/users/me/profile")
                                .with(jwt().jwt(jwt -> jwt.subject(user.getId().toString())))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "nickname": "  ＳｅＢｕ  ",
                                          "grade": 3,
                                          "academicField": "ENGINEERING",
                                          "gpaBand": "GTE_3_5",
                                          "introduction": "정규화 테스트 자기소개"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("SeBu"));

        AppUser saved = appUserRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getNicknameNormalized()).isEqualTo("sebu");
    }

    @Test
    void 대소문자와_호환문자가_다른_중복_닉네임은_409를_반환한다() throws Exception {
        AppUser first = appUserRepository.save(new AppUser("nickname-first@example.com"));
        AppUser second = appUserRepository.save(new AppUser("nickname-second@example.com"));

        updateNickname(first, "SeBu", "첫 번째 자기소개")
                .andExpect(status().isOk());

        updateNickname(second, "ＳＥＢＵ", "두 번째 자기소개")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NICKNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("nickname"))
                .andExpect(jsonPath("$.error.fieldErrors[0].reason").value("DUPLICATE"));
    }

    @Test
    void 자신의_정규화된_닉네임은_다시_저장할_수_있다() throws Exception {
        AppUser user = appUserRepository.save(new AppUser("nickname-self@example.com"));

        updateNickname(user, "SeBu", "첫 번째 자기소개").andExpect(status().isOk());
        updateNickname(user, "ＳＥＢＵ", "두 번째 자기소개")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("SEBU"));
    }

    @Test
    void 예약어와_제로폭_문자가_포함된_닉네임은_400을_반환한다() throws Exception {
        AppUser user = appUserRepository.save(new AppUser("nickname-invalid@example.com"));

        updateNickname(user, "익명", "예약어 테스트")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors[0].reason").value("RESERVED_WORD"));

        updateNickname(user, "세부\u200B사용자", "제로폭 테스트")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].reason").value("INVALID_FORMAT"));

        updateNickname(user, "익명\uFE0F", "변형 선택자 테스트")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].reason").value("INVALID_FORMAT"));
    }

    @Test
    void 잘못된_프로필_enum은_공통_검증_오류로_반환한다() throws Exception {
        AppUser user = appUserRepository.save(new AppUser("profile-invalid-enum@example.com"));

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "세부러",
                                  "grade": 3,
                                  "academicField": "ENGINEERING",
                                  "gpaBand": "INVALID",
                                  "introduction": "잘못된 enum 테스트"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 학년이_범위를_벗어나면_프로필_저장에_실패한다() throws Exception {
        AppUser user = appUserRepository.save(
                new AppUser("invalid-grade@example.com")
        );

        College college = collegeRepository.save(
                new College("검증테스트대학")
        );

        Department major = departmentRepository.save(
                new Department(college, "검증테스트학과")
        );

        String requestBody = """
                {
                  "nickname": "길동이",
                  "grade": 5,
                  "academicField": "ENGINEERING",
                  "gpaBand": "GTE_3_5",
                  "introduction": "머신러닝에 관심이 있습니다."
                }
                """;

        mockMvc.perform(
                        put("/api/v1/users/me/profile")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void 자기소개_검사_시스템에_장애가_발생하면_503을_반환한다() throws Exception {
        AppUser user = appUserRepository.save(
                new AppUser("profile-moderation-error@example.com")
        );

        College college = collegeRepository.save(
                new College("모더레이션장애테스트대학")
        );

        Department major = departmentRepository.save(
                new Department(college, "모더레이션장애테스트학과")
        );

        when(introductionModerator.moderate("검사할 자기소개"))
                .thenThrow(new IntroductionModerationUnavailableException());

        String requestBody = """
                {
                  "nickname": "길동이",
                  "grade": 3,
                  "academicField": "ENGINEERING",
                  "gpaBand": "GTE_3_5",
                  "introduction": "검사할 자기소개"
                }
                """;

        mockMvc.perform(
                        put("/api/v1/users/me/profile")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code")
                .value("CONTENT_MODERATION_UNAVAILABLE"));
    }

    @Test
    void 로그인한_사용자는_회원_탈퇴할_수_있다() throws Exception {
        // given
        AppUser user = appUserRepository.save(
                new AppUser("withdraw@example.com")
        );

        // when
        mockMvc.perform(
                        delete("/api/v1/users/me")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                )
                // then
                .andExpect(status().isNoContent());

        AppUser withdrawnUser = appUserRepository.findById(user.getId())
                .orElseThrow();

        assertThat(withdrawnUser.getDeletedAt()).isNotNull();
        assertThat(withdrawnUser.isDeleted()).isTrue();
    }
    @Test
    void 탈퇴한_사용자는_기존_accessToken으로_마이페이지에_접근할_수_없다() throws Exception {
        // given
        AppUser user = appUserRepository.save(
                new AppUser("withdraw-access@example.com")
        );

        user.withdraw(java.time.LocalDateTime.now());

        // when & then
        mockMvc.perform(
                        get("/api/v1/users/me/mypage")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())

                                        .claim("role", "USER")
                                ))
                )
                .andExpect(status().isUnauthorized());

        updateNickname(user, "탈퇴닉네임", "탈퇴 사용자 수정 시도")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
    }
    @Test
    void 자기소개가_정책에_위반되면_422를_반환하고_프로필은_변경되지_않는다() throws Exception {
        // given
        AppUser user = appUserRepository.save(
                new AppUser("profile-policy@example.com")
        );

        College college = collegeRepository.save(
                new College("정책테스트대학")
        );

        Department major = departmentRepository.save(
                new Department(college, "정책테스트학과")
        );

        when(introductionModerator.moderate("차 단.테-스 트 표현"))
                .thenReturn(
                        new ModerationResult(
                                false,
                                "v1",
                                "test-provider"
                        )
                );

        String requestBody = """
        {
          "nickname": "길동이",
          "grade": 3,
          "academicField": "ENGINEERING",
          "gpaBand": "GTE_3_5",
          "introduction": "차 단.테-스 트 표현"
        }
        """;

        mockMvc.perform(
                        put("/api/v1/users/me/profile")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code")
                        .value("CONTENT_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field")
                        .value("introduction"))
                .andExpect(jsonPath("$.error.fieldErrors[0].reason")
                        .value("INAPPROPRIATE_CONTENT"));

        AppUser savedUser = appUserRepository.findById(user.getId())
                .orElseThrow();

        assertThat(savedUser.getName()).isNull();
        assertThat(savedUser.getGrade()).isNull();
        assertThat(savedUser.getMajorDepartment()).isNull();
        assertThat(savedUser.getGpaBand()).isNull();
        assertThat(savedUser.getIntroduction()).isEmpty();
        assertThat(savedUser.getProfileUpdatedAt()).isNull();
    }

    @Test
    void 자기소개_검사_시스템에_장애가_발생하면_503을_반환하고_프로필은_변경되지_않는다() throws Exception {
        // given
        AppUser user = appUserRepository.save(
                new AppUser("profile-moderation-error@example.com")
        );

        College college = collegeRepository.save(
                new College("모더레이션장애테스트대학")
        );

        Department major = departmentRepository.save(
                new Department(college, "모더레이션장애테스트학과")
        );

        when(introductionModerator.moderate("검사할 자기소개"))
                .thenThrow(new IntroductionModerationUnavailableException());

        String requestBody = """
            {
              "nickname": "길동이",
              "grade": 3,
              "academicField": "ENGINEERING",
              "gpaBand": "GTE_3_5",
              "introduction": "검사할 자기소개"
            }
            """;

        // when & then
        mockMvc.perform(
                        put("/api/v1/users/me/profile")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code")
                        .value("CONTENT_MODERATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.fieldErrors").isArray())
                .andExpect(jsonPath("$.error.fieldErrors").isEmpty());

        // DB가 변경되지 않았는지 확인
        AppUser savedUser = appUserRepository.findById(user.getId())
                .orElseThrow();

        assertThat(savedUser.getName()).isNull();
        assertThat(savedUser.getGrade()).isNull();
        assertThat(savedUser.getMajorDepartment()).isNull();
        assertThat(savedUser.getGpaBand()).isNull();
        assertThat(savedUser.getIntroduction()).isEmpty();
        assertThat(savedUser.getProfileUpdatedAt()).isNull();
    }

    @Test
    void 인증되지_않은_사용자는_북마크_목록을_조회할_수_없다() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me/bookmarked-laboratories")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("ACCESS_TOKEN_INVALID"));
    }

    @Test
    void 존재하지_않는_연구실을_북마크하면_404를_반환한다() throws Exception {
        AppUser user = appUserRepository.save(
                new AppUser("lab-not-found@example.com")
        );

        mockMvc.perform(
                        put("/api/v1/laboratories/{laboratoryId}/bookmark", 999999L)
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code")
                        .value("LABORATORY_NOT_FOUND"));
    }

    @Test
    void 프로필_요청으로_학사_이름과_학과를_변경할_수_없다() throws Exception {
        College college = collegeRepository.save(new College("학사정보보호대학"));
        Department department = departmentRepository.save(new Department(college, "컴퓨터공학과"));
        AppUser user = sejongUser("academic-profile", "홍길동", department);

        String requestBody = """
            {
              "name": "조작된 이름",
              "nickname": null,
              "grade": 3,
              "majorId": "999999",
              "academicField": "ENGINEERING",
              "gpaBand": "GTE_3_5",
              "introduction": "머신러닝에 관심이 있습니다."
            }
            """;

        mockMvc.perform(
                        put("/api/v1/users/me/profile")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.department.id").value(department.getId().toString()))
                .andExpect(jsonPath("$.data.department.name").value("컴퓨터공학과"));
    }

    private AppUser sejongUser(String studentId, String name, Department department) {
        return appUserRepository.save(AppUser.sejong(
                studentId,
                name,
                department.getName(),
                department,
                LocalDateTime.now().minusMinutes(1)
        ));
    }

    private org.springframework.test.web.servlet.ResultActions updateNickname(
            AppUser user,
            String nickname,
            String introduction
    ) throws Exception {
        String requestBody = """
                {
                  "nickname": "%s",
                  "grade": 3,
                  "academicField": "ENGINEERING",
                  "gpaBand": "GTE_3_5",
                  "introduction": "%s"
                }
                """.formatted(nickname, introduction);
        return mockMvc.perform(
                put("/api/v1/users/me/profile")
                        .with(jwt().jwt(jwt -> jwt.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        );
    }
}
