package com.sebu.backend.bookmark.service;

import com.sebu.backend.bookmark.domain.BookmarkId;
import com.sebu.backend.bookmark.domain.BookmarkLimitPolicy;
import com.sebu.backend.bookmark.domain.BookmarkType;
import com.sebu.backend.bookmark.exception.BookmarkLimitExceededException;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.community.bookmark.domain.CommunityPostBookmarkId;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.community.reaction.service.CommunityPostReactionService;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.query.LaboratorySummaryAssembler;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookmarkLimitServiceTest {
    @Mock AppUserRepository userRepository;
    @Mock LaboratoryRepository laboratoryRepository;
    @Mock BookmarkRepository laboratoryBookmarkRepository;
    @Mock LaboratoryResearchFieldRepository researchFieldRepository;
    @Mock LaboratorySummaryAssembler summaryAssembler;
    @Mock CommunityPostRepository postRepository;
    @Mock CommunityPostLikeRepository likeRepository;
    @Mock CommunityPostBookmarkRepository postBookmarkRepository;
    @Mock AppUser user;
    @Mock Laboratory laboratory;

    private BookmarkService laboratoryBookmarkService;
    private CommunityPostReactionService postReactionService;

    @BeforeEach
    void setUp() {
        BookmarkLimitPolicy policy = new BookmarkLimitPolicy();
        laboratoryBookmarkService = new BookmarkService(
                userRepository,
                laboratoryRepository,
                laboratoryBookmarkRepository,
                policy,
                researchFieldRepository,
                summaryAssembler
        );
        postReactionService = new CommunityPostReactionService(
                postRepository,
                likeRepository,
                postBookmarkRepository,
                userRepository,
                policy
        );
    }

    @Test
    void rejectsFiftyFirstLaboratoryBookmark() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(laboratoryRepository.findByIdAndDeletedAtIsNull(51L)).thenReturn(Optional.of(laboratory));
        when(laboratoryBookmarkRepository.existsById(new BookmarkId(1L, 51L))).thenReturn(false);
        when(laboratoryBookmarkRepository.countByUser_IdAndLaboratory_DeletedAtIsNull(1L)).thenReturn(50L);

        assertThatThrownBy(() -> laboratoryBookmarkService.add(1L, 51L))
                .isInstanceOf(BookmarkLimitExceededException.class)
                .satisfies(exception -> {
                    BookmarkLimitExceededException limitException = (BookmarkLimitExceededException) exception;
                    org.assertj.core.api.Assertions.assertThat(limitException.bookmarkType())
                            .isEqualTo(BookmarkType.LABORATORY);
                });

        verify(laboratoryBookmarkRepository, never()).insertIgnore(1L, 51L);
    }

    @Test
    void duplicateLaboratoryBookmarkRemainsIdempotentAtLimit() {
        BookmarkId bookmarkId = new BookmarkId(1L, 10L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(laboratoryRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(laboratory));
        when(laboratoryBookmarkRepository.existsById(bookmarkId)).thenReturn(true);

        laboratoryBookmarkService.add(1L, 10L);

        verify(laboratoryBookmarkRepository, never())
                .countByUser_IdAndLaboratory_DeletedAtIsNull(1L);
        verify(laboratoryBookmarkRepository, never()).insertIgnore(1L, 10L);
    }

    @Test
    void rejectsFiftyFirstPostBookmark() {
        CommunityPostBookmarkId bookmarkId = new CommunityPostBookmarkId(1L, 51L);
        when(postRepository.existsByIdAndDeletedAtIsNull(51L)).thenReturn(true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(postBookmarkRepository.existsById(bookmarkId)).thenReturn(false);
        when(postBookmarkRepository.countByUser_IdAndPost_DeletedAtIsNull(1L)).thenReturn(50L);

        assertThatThrownBy(() -> postReactionService.bookmark(1L, 51L))
                .isInstanceOf(BookmarkLimitExceededException.class)
                .satisfies(exception -> {
                    BookmarkLimitExceededException limitException = (BookmarkLimitExceededException) exception;
                    org.assertj.core.api.Assertions.assertThat(limitException.bookmarkType())
                            .isEqualTo(BookmarkType.POST);
                });

        verify(postBookmarkRepository, never()).insertIgnore(1L, 51L);
    }

    @Test
    void locksUserBeforeReadingPostWhenAddingPostBookmark() {
        CommunityPostBookmarkId bookmarkId = new CommunityPostBookmarkId(1L, 10L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(postRepository.existsByIdAndDeletedAtIsNull(10L)).thenReturn(true);
        when(postBookmarkRepository.existsById(bookmarkId)).thenReturn(false);
        when(postBookmarkRepository.countByUser_IdAndPost_DeletedAtIsNull(1L)).thenReturn(49L);

        postReactionService.bookmark(1L, 10L);

        var ordered = inOrder(userRepository, postRepository, postBookmarkRepository);
        ordered.verify(userRepository).findByIdForUpdate(1L);
        ordered.verify(postRepository).existsByIdAndDeletedAtIsNull(10L);
        ordered.verify(postBookmarkRepository).existsById(bookmarkId);
        ordered.verify(postBookmarkRepository).countByUser_IdAndPost_DeletedAtIsNull(1L);
        ordered.verify(postBookmarkRepository).insertIgnore(1L, 10L);
    }

    @Test
    void duplicatePostBookmarkRemainsIdempotentAtLimit() {
        CommunityPostBookmarkId bookmarkId = new CommunityPostBookmarkId(1L, 10L);
        when(postRepository.existsByIdAndDeletedAtIsNull(10L)).thenReturn(true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(postBookmarkRepository.existsById(bookmarkId)).thenReturn(true);

        postReactionService.bookmark(1L, 10L);

        verify(postBookmarkRepository, never()).countByUser_IdAndPost_DeletedAtIsNull(1L);
        verify(postBookmarkRepository, never()).insertIgnore(1L, 10L);
    }
}
