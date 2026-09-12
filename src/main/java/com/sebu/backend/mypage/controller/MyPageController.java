package com.sebu.backend.mypage.controller;

import com.sebu.backend.auth.controller.AuthCookieFactory;
import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.global.auth.CsrfCookieSupport;
import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.mypage.dto.MyPageResponse;
import com.sebu.backend.mypage.dto.ProfileResponse;
import com.sebu.backend.mypage.dto.ProfileUpdateRequest;
import com.sebu.backend.mypage.service.MyPageService;
import com.sebu.backend.mypage.service.ProfileService;
import com.sebu.backend.account.service.AccountLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me")
@Tag(name = "마이페이지", description = "로그인한 사용자의 마이페이지, 프로필 및 회원 탈퇴 API")
public class MyPageController {

    private final MyPageService myPageService;
    private final CurrentUserProvider currentUserProvider;
    private final ProfileService profileService;
    private final AccountLifecycleService accountLifecycleService;
    private final AuthCookieFactory cookieFactory;
    private final CsrfCookieSupport csrfCookieSupport;


    @Operation(summary = "마이페이지 조회", description = "마이페이지를 조회합니다. data.profile.academicField는 선택한 계열 코드이며 미선택 사용자는 null입니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "마이페이지 조회 성공",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = MyPageOpenApiSchemas.PageResponse.class), examples = @ExampleObject(
                    name = "mypage", value = MyPageOpenApiExamples.MYPAGE_RESPONSE
            ))
    )
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponse<MyPageResponse>> getMyPage(){
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);

        MyPageResponse response = myPageService.getMyPage(userId);

        return ResponseEntity.ok()
                .header("Cache-Control","private, no-store")
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "프로필 수정", description = "프로필과 전공 계열을 함께 저장합니다. academicField는 필수이며 계열만 변경할 때도 다른 프로필 입력값을 함께 보냅니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ProfileUpdateRequest.class),
                            examples = @ExampleObject(name = "engineering", value = MyPageOpenApiExamples.PROFILE_REQUEST))
            ))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", description = "프로필과 계열 저장 성공",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = MyPageOpenApiSchemas.SavedProfileResponse.class), examples = @ExampleObject(
                    name = "saved", value = MyPageOpenApiExamples.PROFILE_RESPONSE
            ))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400", description = "프로필 입력값 오류",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(ref = "#/components/schemas/ErrorApiResponse"),
                    examples = @ExampleObject(name = "invalidAcademicField", value = MyPageOpenApiExamples.INVALID_ACADEMIC_FIELD))
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            ref = "#/components/responses/Conflict"
    )
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);

        ProfileResponse response =
                profileService.updateProfile(userId, request);

        return ResponseEntity.ok()
                .header("Cache-Control", "private, no-store")
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "회원 탈퇴", description = "로그인한 사용자의 계정을 탈퇴 처리합니다.")
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "회원 탈퇴 성공"
    )
    @DeleteMapping
    public ResponseEntity<Void> withdraw(HttpServletRequest request, HttpServletResponse response) {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);

        accountLifecycleService.withdraw(userId);
        csrfCookieSupport.renew(request, response);

        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE,
                cookieFactory.deleteAccess().toString(),
                cookieFactory.deleteRefresh().toString(),
                cookieFactory.deleteRecovery().toString())
            .build();
    }
}
