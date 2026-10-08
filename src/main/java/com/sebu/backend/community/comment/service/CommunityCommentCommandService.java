package com.sebu.backend.community.comment.service;

import com.sebu.backend.community.comment.domain.CommunityComment;
import com.sebu.backend.community.comment.dto.CommentCreateRequest;
import com.sebu.backend.community.comment.dto.CommentCreateResponse;
import com.sebu.backend.community.comment.dto.CommentDeleteResponse;
import com.sebu.backend.community.comment.dto.CommentUpdateRequest;
import com.sebu.backend.community.comment.dto.CommentUpdateResponse;
import com.sebu.backend.community.comment.repository.CommunityCommentRepository;
import com.sebu.backend.community.common.CommunityContentPolicy;
import com.sebu.backend.community.exception.CommentForbiddenException;
import com.sebu.backend.community.exception.CommentNotFoundException;
import com.sebu.backend.community.exception.PostNotFoundException;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.global.auth.ActiveUserCommandGuard;
import com.sebu.backend.user.domain.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityCommentCommandService {
    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final ActiveUserCommandGuard activeUserGuard;
    private final CommunityContentPolicy contentPolicy;
    private final CommunityCommentQueryService queryService;

    @Transactional
    public CommentCreateResponse create(Long userId, Long postId, CommentCreateRequest request) {
        AppUser author = activeUserGuard.lock(userId);
        CommunityPost post = findActivePostForUpdate(postId);
        contentPolicy.validate("content", request.content());

        CommunityComment comment = commentRepository.saveAndFlush(
                new CommunityComment(post, author, request.content())
        );
        return new CommentCreateResponse(
                queryService.toItem(comment, userId),
                commentRepository.countByPost_IdAndDeletedAtIsNull(postId)
        );
    }

    @Transactional
    public CommentUpdateResponse update(
            Long userId,
            Long postId,
            Long commentId,
            CommentUpdateRequest request
    ) {
        activeUserGuard.lock(userId);
        findActivePostForShare(postId);
        CommunityComment comment = findActiveComment(postId, commentId);
        requireOwner(comment, userId);
        contentPolicy.validate("content", request.content());

        comment.updateContent(request.content());
        commentRepository.flush();
        return new CommentUpdateResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUpdatedAt()
        );
    }

    @Transactional
    public CommentDeleteResponse delete(Long userId, Long postId, Long commentId) {
        activeUserGuard.lock(userId);
        findActivePostForUpdate(postId);
        CommunityComment comment = findActiveComment(postId, commentId);
        requireOwner(comment, userId);

        comment.softDelete();
        commentRepository.flush();
        return new CommentDeleteResponse(
                postId,
                commentId,
                commentRepository.countByPost_IdAndDeletedAtIsNull(postId)
        );
    }

    private CommunityPost findActivePostForUpdate(Long postId) {
        return postRepository.findForUpdate(postId)
                .orElseThrow(PostNotFoundException::new);
    }

    private CommunityPost findActivePostForShare(Long postId) {
        return postRepository.findForShare(postId)
                .orElseThrow(PostNotFoundException::new);
    }

    private CommunityComment findActiveComment(Long postId, Long commentId) {
        return commentRepository.findForUpdate(commentId, postId)
                .orElseThrow(CommentNotFoundException::new);
    }

    private void requireOwner(CommunityComment comment, Long userId) {
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new CommentForbiddenException();
        }
    }
}
