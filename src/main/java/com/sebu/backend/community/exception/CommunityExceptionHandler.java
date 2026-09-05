package com.sebu.backend.community.exception;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.bookmark.exception.BookmarkLimitExceededException;
import com.sebu.backend.global.response.ApiResponse;
import com.sebu.backend.mypage.moderation.IntroductionModerationUnavailableException;
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
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.sebu.backend.community")
public class CommunityExceptionHandler {

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

    @ExceptionHandler(AccessTokenInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessTokenInvalid(
            AccessTokenInvalidException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("ACCESS_TOKEN_INVALID", "유효하지 않은 Access Token입니다."));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handlePostNotFound(PostNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("POST_NOT_FOUND", "게시글을 찾을 수 없습니다."));
    }

    @ExceptionHandler(PostForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handlePostForbidden(PostForbiddenException exception) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("POST_FORBIDDEN", "게시글을 수정하거나 삭제할 권한이 없습니다."));
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommentNotFound(CommentNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."));
    }

    @ExceptionHandler(CommentForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleCommentForbidden(CommentForbiddenException exception) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("COMMENT_FORBIDDEN", "댓글을 수정하거나 삭제할 권한이 없습니다."));
    }

    @ExceptionHandler({InvalidPostQueryException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidQueryParameter(Exception exception) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure("INVALID_QUERY_PARAMETER", "조회 조건을 확인해 주세요."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", "입력값을 확인해 주세요."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<ApiResponse.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new ApiResponse.FieldError(
                        fieldError.getField(),
                        "INVALID_VALUE",
                        fieldError.getDefaultMessage()
                ))
                .toList();
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.failure(
                        "VALIDATION_ERROR",
                        "입력값을 확인해 주세요.",
                        fieldErrors,
                        null
                ));
    }

    @ExceptionHandler(CommunityContentPolicyViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleContentPolicyViolation(
            CommunityContentPolicyViolationException exception
    ) {
        return ResponseEntity
                .unprocessableEntity()
                .body(ApiResponse.failure(
                        "CONTENT_POLICY_VIOLATION",
                        "입력 내용을 확인해 주세요.",
                        List.of(new ApiResponse.FieldError(
                                exception.getField(),
                                "INAPPROPRIATE_CONTENT",
                                "사용할 수 없는 표현이 포함되어 있습니다."
                        )),
                        null
                ));
    }

    @ExceptionHandler(IntroductionModerationUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleContentModerationUnavailable(
            IntroductionModerationUnavailableException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(
                        "CONTENT_MODERATION_UNAVAILABLE",
                        "입력 내용을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요."
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedRuntimeException(
            RuntimeException exception
    ) {
        log.error("Unexpected community API failure", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        "INTERNAL_SERVER_ERROR",
                        "서버 오류가 발생했습니다."
                ));
    }
}
