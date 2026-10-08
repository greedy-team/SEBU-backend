package com.sebu.backend.community.comment.service;

import com.sebu.backend.community.comment.domain.CommunityComment;
import com.sebu.backend.community.comment.dto.CommentListResponse;
import com.sebu.backend.community.comment.repository.CommunityCommentRepository;
import com.sebu.backend.community.common.CommunityAuthorAssembler;
import com.sebu.backend.community.common.dto.CommunityAuthorResponse;
import com.sebu.backend.community.exception.InvalidPostQueryException;
import com.sebu.backend.community.exception.PostNotFoundException;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.global.auth.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityCommentQueryService {
    private static final int MAX_PAGE_SIZE = 50;

    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CommunityAuthorAssembler authorAssembler;

    @Transactional(readOnly = true)
    public CommentListResponse findComments(Long postId, int page, int size) {
        validatePage(page, size);
        var post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);
        Long postAuthorId = post.getAuthor().getId();

        Long viewerId = currentUserProvider.currentUserId().orElse(null);
        Page<CommunityComment> result = commentRepository
                .findByPost_IdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                        postId,
                        PageRequest.of(page, size)
                );

        var authors = authorAssembler.toResponses(result.getContent().stream()
                .map(CommunityComment::getAuthor).toList());
        return new CommentListResponse(
                result.getContent().stream()
                        .map(comment -> toItem(comment, viewerId, postAuthorId,
                                authors.get(comment.getAuthor().getId())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext()
        );
    }

    CommentListResponse.CommentItem toItem(CommunityComment comment, Long viewerId) {
        return toItem(comment, viewerId, comment.getPost().getAuthor().getId(),
                authorAssembler.toResponse(comment.getAuthor()));
    }

    private CommentListResponse.CommentItem toItem(
            CommunityComment comment, Long viewerId, Long postAuthorId, CommunityAuthorResponse author) {
        return new CommentListResponse.CommentItem(
                comment.getId(),
                author,
                comment.getContent(),
                viewerId != null && comment.getAuthor().getId().equals(viewerId),
                comment.getAuthor().getId().equals(postAuthorId),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPostQueryException();
        }
    }
}
