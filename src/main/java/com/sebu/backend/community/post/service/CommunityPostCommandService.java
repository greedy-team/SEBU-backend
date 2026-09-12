package com.sebu.backend.community.post.service;

import com.sebu.backend.community.common.CommunityContentPolicy;
import com.sebu.backend.community.exception.PostForbiddenException;
import com.sebu.backend.community.exception.PostNotFoundException;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.dto.PostCreateRequest;
import com.sebu.backend.community.post.dto.PostCreateResponse;
import com.sebu.backend.community.post.dto.PostDeleteResponse;
import com.sebu.backend.community.post.dto.PostUpdateRequest;
import com.sebu.backend.community.post.dto.PostUpdateResponse;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.global.auth.ActiveUserCommandGuard;
import com.sebu.backend.user.domain.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityPostCommandService {
    private final CommunityPostRepository postRepository;
    private final ActiveUserCommandGuard activeUserGuard;
    private final CommunityContentPolicy contentPolicy;

    @Transactional
    public PostCreateResponse create(Long userId, PostCreateRequest request) {
        AppUser author = activeUserGuard.lock(userId);
        validateContent(request.title(), request.content());

        CommunityPost post = postRepository.save(new CommunityPost(
                author,
                request.category(),
                request.title(),
                request.content()
        ));
        return new PostCreateResponse(post.getId());
    }

    @Transactional
    public PostUpdateResponse update(Long userId, Long postId, PostUpdateRequest request) {
        activeUserGuard.lock(userId);
        CommunityPost post = findActivePost(postId);
        requireOwner(post, userId);
        validateContent(request.title(), request.content());

        post.update(request.category(), request.title(), request.content());
        postRepository.flush();
        return new PostUpdateResponse(post.getId(), post.getUpdatedAt());
    }

    @Transactional
    public PostDeleteResponse delete(Long userId, Long postId) {
        activeUserGuard.lock(userId);
        CommunityPost post = findActivePost(postId);
        requireOwner(post, userId);

        post.softDelete();
        postRepository.flush();
        return new PostDeleteResponse(post.getId());
    }

    private CommunityPost findActivePost(Long postId) {
        return postRepository.findForUpdate(postId)
                .orElseThrow(PostNotFoundException::new);
    }

    private void requireOwner(CommunityPost post, Long userId) {
        if (!post.getAuthor().getId().equals(userId)) {
            throw new PostForbiddenException();
        }
    }

    private void validateContent(String title, String content) {
        contentPolicy.validate("title", title);
        contentPolicy.validate("content", content);
    }
}
