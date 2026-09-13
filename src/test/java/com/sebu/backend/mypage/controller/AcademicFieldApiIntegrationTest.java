package com.sebu.backend.mypage.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sebu.backend.auth.token.JwtAccessTokenService;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.mypage.moderation.IntroductionModerationUnavailableException;
import com.sebu.backend.mypage.moderation.IntroductionModerator;
import com.sebu.backend.mypage.moderation.ModerationResult;
import com.sebu.backend.user.domain.AcademicField;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.Nickname;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.sebu.backend.support.CookieApiRequests.ORIGIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Requests own their transactions so a failed save is checked against committed database state. */
@SpringBootTest
@AutoConfigureMockMvc
class AcademicFieldApiIntegrationTest {
    private static final String PROFILE_URL = "/api/v1/users/me/profile";
    private static final String MYPAGE_URL = "/api/v1/users/me/mypage";
    private static final LocalDateTime ORIGINAL_TIME = LocalDateTime.of(2026, 9, 10, 11, 0);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AppUserRepository users;
    @Autowired CollegeRepository colleges;
    @Autowired DepartmentRepository departments;
    @Autowired JwtAccessTokenService tokens;
    @MockitoBean IntroductionModerator moderator;

    private final List<Long> userIds = new ArrayList<>();
    private College college;
    private Department department;
    private AppUser user;
    private Cookie access;
    private Cookie csrf;

    @BeforeEach
    void setUp() throws Exception {
        when(moderator.moderate(anyString())).thenReturn(new ModerationResult(true, "v1", "test"));
        college = colleges.save(new College("계열-" + UUID.randomUUID()));
        department = departments.save(new Department(college, "컴퓨터공학과"));
        user = createUser();
        access = new Cookie("access_token", tokens.issue(user.getId(), user.getAuthVersion()));
        csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent())
            .andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @AfterEach
    void cleanUp() {
        users.deleteAllById(userIds);
        if (department != null) departments.deleteById(department.getId());
        if (college != null) colleges.deleteById(college.getId());
    }

    @ParameterizedTest
    @EnumSource(AcademicField.class)
    void savesEveryCodeAndReturnsItAtTheDocumentedPaths(AcademicField field) throws Exception {
        save(body(field)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.academicField").value(field.name()))
            .andExpect(jsonPath("$.data.department.id").value(department.getId().toString()))
            .andExpect(jsonPath("$.data.name").value("홍길동"))
            .andExpect(jsonPath("$.data.profileCompleted").value(true))
            .andExpect(jsonPath("$.data.profile").doesNotExist());

        mvc.perform(get(MYPAGE_URL).cookie(access)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.data.profile.academicField").value(field.name()))
            .andExpect(jsonPath("$.data.summary.bookmarkedLaboratoryCount").value(0))
            .andExpect(jsonPath("$.data.bookmarkedLaboratories.items").isEmpty());
        assertThat(users.findById(user.getId()).orElseThrow().getAcademicField()).isEqualTo(field);
    }

    @Test
    void legacyUserHasAnExplicitNullWithoutChangingCompletionOrTimestamp() throws Exception {
        JsonNode profile = mapper.readTree(mvc.perform(get(MYPAGE_URL).cookie(access))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
            .path("data").path("profile");
        assertThat(profile.has("academicField")).isTrue();
        assertThat(profile.get("academicField").isNull()).isTrue();
        assertThat(profile.path("profileCompleted").asBoolean()).isTrue();
        assertThat(users.findById(user.getId()).orElseThrow().getProfileUpdatedAt()).isEqualTo(ORIGINAL_TIME);
    }

    @Test
    void fieldOnlyChangeUpdatesTimestampAndAnIdenticalRequestKeepsIt() throws Exception {
        AppUser initial = users.findById(user.getId()).orElseThrow();
        initial.selectAcademicField(AcademicField.ENGINEERING, ORIGINAL_TIME);
        users.saveAndFlush(initial);

        save(body(AcademicField.NATURAL_SCIENCE)).andExpect(status().isOk());
        AppUser changed = users.findById(user.getId()).orElseThrow();
        assertThat(changed.getProfileUpdatedAt()).isAfter(ORIGINAL_TIME);
        assertThat(changed.getGrade()).isEqualTo((short) 3);
        assertThat(changed.getName()).isEqualTo("홍길동");
        assertThat(changed.getSejongDepartmentName()).isEqualTo(department.getName());
        save(body(AcademicField.NATURAL_SCIENCE)).andExpect(status().isOk());
        assertThat(users.findById(user.getId()).orElseThrow().getProfileUpdatedAt())
            .isEqualTo(changed.getProfileUpdatedAt());
    }

    @Test
    void missingFieldIsRejected() throws Exception {
        ObjectNode request = body(AcademicField.ENGINEERING);
        request.remove("academicField");
        assertInvalid(save(request), "계열을 선택해 주세요.");
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    void nullAndBlankAreRejected(String jsonValue) throws Exception {
        ObjectNode request = body(AcademicField.ENGINEERING);
        request.set("academicField", mapper.readTree(jsonValue));
        assertInvalid(save(request), "계열을 선택해 주세요.");
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEDICAL_HEALTH", "공학계열", "engineering", " ENGINEERING ", "0"})
    void rejectsUnsupportedCodesWithoutNormalizingThem(String code) throws Exception {
        ObjectNode request = body(AcademicField.ENGINEERING).put("academicField", code);
        assertInvalid(save(request), "지원하지 않는 계열입니다. 목록에서 다시 선택해 주세요.");
        assertUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "4", "1.5", "true", "false", "[]", "[\"ENGINEERING\"]", "{}"})
    void rejectsNonStringTypesIncludingEnumOrdinals(String jsonValue) throws Exception {
        ObjectNode request = body(AcademicField.ENGINEERING);
        request.set("academicField", mapper.readTree(jsonValue));
        assertInvalid(save(request), "계열은 지정된 문자열 코드 하나로 전달해 주세요.");
        assertUnchanged();
    }

    @Test
    void aRejectedRequestCannotClearAnExistingSelection() throws Exception {
        save(body(AcademicField.ENGINEERING)).andExpect(status().isOk());
        ObjectNode request = body(AcademicField.ENGINEERING).putNull("academicField");
        assertInvalid(save(request), "계열을 선택해 주세요.");
        assertThat(users.findById(user.getId()).orElseThrow().getAcademicField()).isEqualTo(AcademicField.ENGINEERING);
    }

    @Test
    void malformedJsonKeepsTheExistingGenericErrorShape() throws Exception {
        mvc.perform(protectedPut().content("{broken"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.fieldErrors").isEmpty());
        assertUnchanged();
    }

    @Test
    void moderationRejectionAndOutageDoNotSaveAnyProfileChanges() throws Exception {
        ObjectNode request = body(AcademicField.ENGINEERING).put("nickname", "새닉네임").put("grade", 4)
            .put("introduction", "검증할 자기소개");
        when(moderator.moderate("검증할 자기소개")).thenReturn(new ModerationResult(false, "v1", "test"));
        save(request).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("CONTENT_POLICY_VIOLATION"));
        assertUnchanged();

        when(moderator.moderate("검증할 자기소개")).thenThrow(new IntroductionModerationUnavailableException());
        save(request).andExpect(status().isServiceUnavailable());
        assertUnchanged();
    }

    @Test
    void nicknameConflictDoesNotSaveTheFieldOrOtherChanges() throws Exception {
        AppUser other = createUser();
        other.updateProfile(Nickname.from("taken-" + other.getId()), (short) 3, null, "", ORIGINAL_TIME, "v1", "test");
        users.saveAndFlush(other);
        save(body(AcademicField.ENGINEERING).put("nickname", other.getNickname()).put("grade", 4))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("NICKNAME_ALREADY_EXISTS"));
        assertUnchanged();
    }

    @Test
    void requestUserIdCannotChangeAnotherUsersProfile() throws Exception {
        AppUser other = createUser();
        save(body(AcademicField.ENGINEERING).put("userId", other.getId())).andExpect(status().isOk());
        assertThat(users.findById(user.getId()).orElseThrow().getAcademicField()).isEqualTo(AcademicField.ENGINEERING);
        assertThat(users.findById(other.getId()).orElseThrow().getAcademicField()).isNull();
    }

    @Test
    void savesRequireCookieAuthenticationCsrfAndTrustedOrigin() throws Exception {
        String request = body(AcademicField.ENGINEERING).toString();
        mvc.perform(put(PROFILE_URL).header("Origin", ORIGIN).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isUnauthorized());
        mvc.perform(put(PROFILE_URL).cookie(access).header("Origin", ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(put(PROFILE_URL).cookie(access, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .header("Origin", "https://untrusted.example").contentType(MediaType.APPLICATION_JSON).content(request))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));
        assertUnchanged();
    }

    private AppUser createUser() {
        AppUser created = AppUser.sejong(UUID.randomUUID().toString(), "홍길동", department.getName(), department, ORIGINAL_TIME);
        created.updateGrade(3, ORIGINAL_TIME);
        created = users.saveAndFlush(created);
        userIds.add(created.getId());
        return created;
    }

    private ObjectNode body(AcademicField field) {
        return mapper.createObjectNode().putNull("nickname").put("grade", 3)
            .put("academicField", field.name()).putNull("gpaBand").put("introduction", "");
    }

    private MockHttpServletRequestBuilder protectedPut() {
        return put(PROFILE_URL).cookie(access, csrf).header("Origin", ORIGIN)
            .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions save(ObjectNode request) throws Exception {
        return mvc.perform(protectedPut().content(request.toString()));
    }

    private void assertInvalid(ResultActions result, String message) throws Exception {
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.fieldErrors[0].field").value("academicField"))
            .andExpect(jsonPath("$.error.fieldErrors[0].reason").value("INVALID_VALUE"))
            .andExpect(jsonPath("$.error.fieldErrors[0].message").value(message));
    }

    private void assertUnchanged() {
        AppUser saved = users.findById(user.getId()).orElseThrow();
        assertThat(saved.getAcademicField()).isNull();
        assertThat(saved.getNickname()).isNull();
        assertThat(saved.getGrade()).isEqualTo((short) 3);
        assertThat(saved.getIntroduction()).isEmpty();
        assertThat(saved.getProfileUpdatedAt()).isEqualTo(ORIGINAL_TIME);
    }
}
