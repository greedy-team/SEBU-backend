package com.sebu.backend.community.post.service;

import com.sebu.backend.community.bookmark.domain.CommunityPostBookmarkId;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.comment.repository.CommunityCommentRepository;
import com.sebu.backend.community.common.CommunityAuthorAssembler;
import com.sebu.backend.community.common.repository.PostCountProjection;
import com.sebu.backend.community.exception.InvalidPostQueryException;
import com.sebu.backend.community.exception.PostNotFoundException;
import com.sebu.backend.community.like.domain.CommunityPostLikeId;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.community.post.domain.CommunityPostSort;
import com.sebu.backend.community.post.dto.PostDetailResponse;
import com.sebu.backend.community.post.dto.PostListResponse;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.global.auth.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunityPostQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int HOT_POST_LIMIT = 4;

    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityPostLikeRepository likeRepository;
    private final CommunityPostBookmarkRepository bookmarkRepository;
    private final CurrentUserProvider currentUserProvider;
    private final CommunityAuthorAssembler authorAssembler;

    @Transactional(readOnly = true)
    public PostListResponse findPosts(
            String keyword,
            CommunityPostCategory category,
            CommunityPostSort sort,
            int page,
            int size
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        validatePage(page, size);
        CommunityPostSort resolvedSort = sort == null ? CommunityPostSort.LATEST : sort;
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<CommunityPost> result = resolvedSort == CommunityPostSort.POPULAR
                ? postRepository.findPopular(category, normalizedKeyword, pageRequest)
                : postRepository.findLatest(category, normalizedKeyword, pageRequest);

        List<Long> postIds = result.getContent().stream().map(CommunityPost::getId).toList();
        Map<Long, Long> likeCounts = postIds.isEmpty()
                ? Map.of()
                : countMap(likeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = postIds.isEmpty()
                ? Map.of()
                : countMap(commentRepository.countActiveByPostIds(postIds));
        Set<Long> hotPostIds = findHotPostIds();
        var authors = authorAssembler.toResponses(result.getContent().stream()
                .map(CommunityPost::getAuthor).toList());

        List<PostListResponse.PostSummary> posts = result.getContent().stream()
                .map(post -> new PostListResponse.PostSummary(
                        post.getId(),
                        post.getCategory(),
                        post.getTitle(),
                        authors.get(post.getAuthor().getId()),
                        badges(post, hotPostIds),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        commentCounts.getOrDefault(post.getId(), 0L),
                        post.getViewCount(),
                        post.getCreatedAt()
                ))
                .toList();

        return new PostListResponse(
                posts,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.hasNext()
        );
    }

    @Transactional
    public PostDetailResponse findDetail(Long postId) {
        if (postRepository.increaseViewCount(postId) == 0) {
            throw new PostNotFoundException();
        }
        postRepository.flush();
        CommunityPost post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);

        Long viewerId = currentUserProvider.currentUserId().orElse(null);
        long likeCount = likeRepository.countActiveByPostId(postId);
        long commentCount = commentRepository.countByPost_IdAndDeletedAtIsNull(postId);
        boolean liked = viewerId != null
                && likeRepository.existsById(new CommunityPostLikeId(viewerId, postId));
        boolean bookmarked = viewerId != null
                && bookmarkRepository.existsById(new CommunityPostBookmarkId(viewerId, postId));
        boolean mine = viewerId != null && post.getAuthor().getId().equals(viewerId);

        PostDetailResponse.Post response = new PostDetailResponse.Post(
                post.getId(),
                post.getCategory(),
                post.getTitle(),
                post.getContent(),
                authorAssembler.toResponse(post.getAuthor()),
                badges(post, findHotPostIds()),
                post.getViewCount(),
                likeCount,
                commentCount,
                liked,
                bookmarked,
                mine,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
        return new PostDetailResponse(response);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.strip();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new InvalidPostQueryException();
        }
        return escapeLikePattern(normalized);
    }

    private String escapeLikePattern(String keyword) {
        return keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPostQueryException();
        }
    }

    private Map<Long, Long> countMap(List<PostCountProjection> counts) {
        Map<Long, Long> result = new HashMap<>();
        for (PostCountProjection count : counts) {
            result.put(count.getPostId(), count.getCount());
        }
        return result;
    }

    private Set<Long> findHotPostIds() {
        return new HashSet<>(postRepository.findPopular(
                        null,
                        null,
                        PageRequest.of(0, HOT_POST_LIMIT)
                ).getContent().stream()
                .map(CommunityPost::getId)
                .toList());
    }

    private List<String> badges(CommunityPost post, Set<Long> hotPostIds) {
        boolean hot = hotPostIds.contains(post.getId());
        boolean recent = !post.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24));
        if (hot && recent) {
            return List.of("HOT", "NEW");
        }
        if (hot) {
            return List.of("HOT");
        }
        if (recent) {
            return List.of("NEW");
        }
        return List.of();
    }
}
