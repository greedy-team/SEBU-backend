package com.sebu.backend.global.exception;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.bookmark.exception.BookmarkLimitExceededException;
import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.laboratory.exception.InvalidLaboratoryPageException;
import com.sebu.backend.laboratory.exception.InvalidLaboratorySizeException;
import com.sebu.backend.laboratory.exception.LaboratoryNotFoundException;
import com.sebu.backend.laboratoryreview.exception.InvalidLaboratoryReviewInputException;
import com.sebu.backend.laboratoryreview.exception.InvalidReviewPageException;
import com.sebu.backend.laboratoryreview.exception.InvalidReviewSizeException;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewAlreadyExistsException;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewForbiddenException;
import com.sebu.backend.laboratoryreview.exception.LaboratoryReviewNotFoundException;
import com.sebu.backend.mypage.moderation.IntroductionModerationException;
import com.sebu.backend.mypage.moderation.IntroductionModerationUnavailableException;
import com.sebu.backend.user.exception.InvalidNicknameException;
import com.sebu.backend.user.exception.NicknameAlreadyExistsException;
import com.sebu.backend.user.exception.ProfileUpdateConflictException;
import com.sebu.backend.user.exception.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ExceptionControllerAdvice {

    @ExceptionHandler(BookmarkLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleBookmarkLimitExceeded(
            BookmarkLimitExceededException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(
                        "BOOKMARK_LIMIT_EXCEEDED",
                        exception.userMessage()
                ));
    }

    @ExceptionHandler(NicknameAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleNicknameAlreadyExists(
            NicknameAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(
                        "NICKNAME_ALREADY_EXISTS",
                        "이미 사용 중인 닉네임입니다.",
                        List.of(new ApiResponse.FieldError(
                                "nickname",
                                "DUPLICATE",
                                "다른 닉네임을 입력해 주세요."
                        )),
                        null
                ));
    }

    @ExceptionHandler(InvalidNicknameException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidNickname(
            InvalidNicknameException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        "VALIDATION_ERROR",
                        "입력값을 확인해 주세요.",
                        List.of(new ApiResponse.FieldError(
                                "nickname",
                                exception.reason(),
                                exception.userMessage()
                        )),
                        null
                ));
    }

    @ExceptionHandler(ProfileUpdateConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileUpdateConflict(
            ProfileUpdateConflictException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(
                        "PROFILE_UPDATE_CONFLICT",
                        "프로필이 변경되었습니다. 최신 정보를 확인한 후 다시 시도해 주세요."
                ));
    }

    @ExceptionHandler(IntroductionModerationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntroductionModeration(
            IntroductionModerationException exception
    ) {
        return ResponseEntity
                .unprocessableEntity()
                .body(ApiResponse.failure(
                        "CONTENT_POLICY_VIOLATION",
                        "입력 내용을 확인해 주세요.",
                        List.of(
                                new ApiResponse.FieldError(
                                        "introduction",
                                        "INAPPROPRIATE_CONTENT",
                                        "자기소개에 사용할 수 없는 표현이 포함되어 있습니다. 욕설이나 선정적인 표현을 수정해 주세요."
                                )
                        ),
                        null
                ));
    }
    @ExceptionHandler(IntroductionModerationUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntroductionModerationUnavailable(
            IntroductionModerationUnavailableException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(
                        "CONTENT_MODERATION_UNAVAILABLE",
                        "자기소개를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                        List.of(),
                        null
                ));
    }

    @ExceptionHandler(AccessTokenInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessTokenInvalid(
            AccessTokenInvalidException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(
                        "ACCESS_TOKEN_INVALID",
                        "유효하지 않은 Access Token입니다."
                ));
    }

    @ExceptionHandler(LaboratoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleLaboratoryNotFound(
            LaboratoryNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(
                        "LABORATORY_NOT_FOUND",
                        "연구실을 찾을 수 없습니다."
                ));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(
            UserNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."
                ));
    }

    @ExceptionHandler(LaboratoryReviewNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleLaboratoryReviewNotFound(
            LaboratoryReviewNotFoundException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(
                        "LABORATORY_REVIEW_NOT_FOUND",
                        "랩실 후기를 찾을 수 없습니다."
                ));
    }

    @ExceptionHandler(LaboratoryReviewAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleLaboratoryReviewAlreadyExists(
            LaboratoryReviewAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(
                        "LABORATORY_REVIEW_ALREADY_EXISTS",
                        "이미 해당 연구실에 작성한 후기가 있습니다."
                ));
    }

    @ExceptionHandler(LaboratoryReviewForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleLaboratoryReviewForbidden(
            LaboratoryReviewForbiddenException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure(
                        "LABORATORY_REVIEW_FORBIDDEN",
                        "해당 후기를 수정하거나 삭제할 권한이 없습니다."
                ));
    }

    @ExceptionHandler(InvalidReviewPageException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidReviewPage(
            InvalidReviewPageException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        "INVALID_REVIEW_PAGE",
                        "페이지 번호는 0 이상이어야 합니다."
                ));
    }

    @ExceptionHandler(InvalidReviewSizeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidReviewSize(
            InvalidReviewSizeException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        "INVALID_REVIEW_SIZE",
                        "조회 개수는 1~50이어야 합니다."
                ));
    }

    @ExceptionHandler(InvalidLaboratoryReviewInputException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleInvalidLaboratoryReviewInput(
            InvalidLaboratoryReviewInputException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        "VALIDATION_ERROR",
                        "입력값을 확인해 주세요.",
                        List.of(new ApiResponse.FieldError(
                                exception.field(),
                                exception.reason(),
                                exception.userMessage()
                        )),
                        null
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<ApiResponse.FieldError> fieldErrors =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .map(fieldError ->
                                new ApiResponse.FieldError(
                                        fieldError.getField(),
                                        "INVALID_VALUE",
                                        fieldError.getDefaultMessage()
                                )
                        )
                        .toList();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(
                        "VALIDATION_ERROR",
                        "입력값을 확인해 주세요.",
                        fieldErrors,
                        null
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        "VALIDATION_ERROR",
                        "입력값을 확인해 주세요."
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        "INVALID_QUERY_PARAMETER",
                        "조회 조건을 확인해 주세요."
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedRuntimeException(
            RuntimeException exception
    ) {
        log.error("Unexpected API failure", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "INTERNAL_SERVER_ERROR",
                        "서버 오류가 발생했습니다."
                ));
    }

    @ExceptionHandler(InvalidLaboratoryPageException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidLaboratoryPage(
            InvalidLaboratoryPageException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(
                        "INVALID_LABORATORY_PAGE",
                        "페이지는 0 이상이어야 합니다."
                ));
    }

    @ExceptionHandler(InvalidLaboratorySizeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidLaboratorySize(
            InvalidLaboratorySizeException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(
                        "INVALID_LABORATORY_SIZE",
                        "조회 개수는 1 이상 50 이하여야 합니다."
                ));
    }
}
