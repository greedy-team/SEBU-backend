package com.sebu.backend.community.reaction.service;

import com.sebu.backend.bookmark.domain.BookmarkLimitPolicy;
import com.sebu.backend.bookmark.domain.BookmarkType;
import com.sebu.backend.community.bookmark.domain.CommunityPostBookmarkId;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.exception.PostNotFoundException;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.community.reaction.dto.PostBookmarkResponse;
import com.sebu.backend.community.reaction.dto.PostLikeResponse;
import com.sebu.backend.global.auth.ActiveUserCommandGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPostReactionService {
    private final CommunityPostRepository postRepository;
    private final CommunityPostLikeRepository likeRepository;
    private final CommunityPostBookmarkRepository bookmarkRepository;
    private final ActiveUserCommandGuard activeUserGuard;
    private final BookmarkLimitPolicy bookmarkLimitPolicy;

    @Transactional
    public PostLikeResponse like(Long userId, Long postId) {
        activeUserGuard.lock(userId);
        requireActivePost(postId);
        likeRepository.insertIgnore(userId, postId);
        return new PostLikeResponse(true, likeRepository.countActiveByPostId(postId));
    }

    @Transactional
    public PostLikeResponse unlike(Long userId, Long postId) {
        activeUserGuard.lock(userId);
        requireActivePost(postId);
        likeRepository.deleteByUserIdAndPostId(userId, postId);
        return new PostLikeResponse(false, likeRepository.countActiveByPostId(postId));
    }

    @Transactional
    public PostBookmarkResponse bookmark(Long userId, Long postId) {
        activeUserGuard.lock(userId);
        requireActivePost(postId);

        CommunityPostBookmarkId bookmarkId = new CommunityPostBookmarkId(userId, postId);
        if (bookmarkRepository.existsById(bookmarkId)) {
            return new PostBookmarkResponse(true);
        }

        bookmarkLimitPolicy.validateNewBookmark(
                BookmarkType.POST,
                bookmarkRepository.countByUser_IdAndPost_DeletedAtIsNull(userId)
        );
        bookmarkRepository.insertIgnore(userId, postId);
        return new PostBookmarkResponse(true);
    }

    @Transactional
    public PostBookmarkResponse unbookmark(Long userId, Long postId) {
        activeUserGuard.lock(userId);
        requireActivePost(postId);
        bookmarkRepository.deleteByUserIdAndPostId(userId, postId);
        return new PostBookmarkResponse(false);
    }

    private void requireActivePost(Long postId) {
        postRepository.findForUpdate(postId).orElseThrow(PostNotFoundException::new);
    }
}
