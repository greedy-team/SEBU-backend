package com.sebu.backend.bookmark.controller;

import com.sebu.backend.auth.exception.AccessTokenInvalidException;
import com.sebu.backend.bookmark.dto.BookmarkedLaboratoriesResponse;
import com.sebu.backend.bookmark.service.BookmarkService;
import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "연구실 북마크",
        description = "연구실 북마크 조회 및 관리 API"
)
@RestController
@RequiredArgsConstructor
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(
            summary = "북마크한 연구실 목록 조회",
            description = "로그인한 사용자가 북마크한 연구실 목록을 조회합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @GetMapping("/api/v1/users/me/bookmarked-laboratories")
    public ResponseEntity<ApiResponse<BookmarkedLaboratoriesResponse>>
    getBookmarkedLaboratories() {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);

        BookmarkedLaboratoriesResponse response =
                bookmarkService.getBookmarkedLaboratories(userId);

        return ResponseEntity.ok()
                .header("Cache-Control", "private, no-store")
                .body(ApiResponse.success(response));
    }

    @Operation(
            summary = "연구실 북마크 추가",
            description = "로그인한 사용자가 연구실을 북마크에 추가합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "연구실 북마크 추가 성공"
    )
    @PutMapping("/api/v1/laboratories/{laboratoryId}/bookmark")
    public ResponseEntity<Void> addBookmark(
            @PathVariable Long laboratoryId
    ) {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);

        bookmarkService.add(userId, laboratoryId);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "연구실 북마크 삭제",
            description = "로그인한 사용자가 연구실 북마크를 삭제합니다."
    )
    @SecurityRequirement(name = "cookieAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "연구실 북마크 삭제 성공"
    )
    @DeleteMapping("/api/v1/laboratories/{laboratoryId}/bookmark")
    public ResponseEntity<Void> deleteBookmark(
            @PathVariable Long laboratoryId
    ) {
        Long userId = currentUserProvider.currentUserId()
                .orElseThrow(AccessTokenInvalidException::new);

        bookmarkService.remove(userId, laboratoryId);

        return ResponseEntity.noContent().build();
    }
}
