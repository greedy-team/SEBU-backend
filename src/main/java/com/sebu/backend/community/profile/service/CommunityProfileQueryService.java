package com.sebu.backend.community.profile.service;

import com.sebu.backend.college.domain.College;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.comment.repository.CommunityCommentRepository;
import com.sebu.backend.community.common.CommunityAuthorMapper;
import com.sebu.backend.community.common.repository.PostCountProjection;
import com.sebu.backend.community.exception.InvalidPostQueryException;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.community.profile.dto.CommunityProfileResponse;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.exception.UserNotFoundException;
import com.sebu.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommunityProfileQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final long FIRST_POST_MIN_COUNT = 1;
    private static final long POPULAR_AUTHOR_MIN_BOOKMARK_COUNT = 5;

    private final AppUserRepository appUserRepository;
    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityPostLikeRepository likeRepository;
    private final CommunityPostBookmarkRepository bookmarkRepository;
    private final CommunityAuthorMapper authorMapper;

    @Transactional(readOnly = true)
    public CommunityProfileResponse findProfile(Long userId, int page, int size) {
        validatePage(page, size);
        AppUser user = appUserRepository.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(UserNotFoundException::new);

        Page<CommunityPost> posts = postRepository
                .findByAuthor_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                        userId,
                        PageRequest.of(page, size)
        );
        List<Long> postIds = posts.getContent().stream().map(CommunityPost::getId).toList();
        Map<Long, Long> likeCounts = postIds.isEmpty()
                ? Map.of()
                : countMap(likeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = postIds.isEmpty()
                ? Map.of()
                : countMap(commentRepository.countActiveByPostIds(postIds));
        long writtenPostCount = posts.getTotalElements();
        long receivedBookmarkCount = writtenPostCount == 0
                ? 0
                : bookmarkRepository.countActiveReceivedByAuthorId(userId);

        CommunityProfileResponse.Profile profile = new CommunityProfileResponse.Profile(
                user.getId(),
                authorMapper.displayNickname(user),
                user.getGrade(),
                toDepartment(user.getMajorDepartment()),
                user.getCreatedAt(),
                user.getIntroduction(),
                badges(writtenPostCount, receivedBookmarkCount)
        );
        CommunityProfileResponse.Stats stats = new CommunityProfileResponse.Stats(
                writtenPostCount,
                likeRepository.countReceivedByAuthorId(userId),
                commentRepository.countActiveByAuthorId(userId)
        );
        CommunityProfileResponse.Posts responsePosts = new CommunityProfileResponse.Posts(
                posts.getContent().stream()
                        .map(post -> new CommunityProfileResponse.PostItem(
                                post.getId(),
                                post.getCategory(),
                                post.getTitle(),
                                likeCounts.getOrDefault(post.getId(), 0L),
                                commentCounts.getOrDefault(post.getId(), 0L),
                                post.getViewCount(),
                                post.getCreatedAt()
                        ))
                        .toList(),
                posts.getNumber(),
                posts.getSize(),
                posts.getTotalElements(),
                posts.hasNext()
        );
        return new CommunityProfileResponse(profile, stats, responsePosts);
    }

    private List<CommunityProfileResponse.Badge> badges(
            long writtenPostCount,
            long receivedBookmarkCount
    ) {
        List<CommunityProfileResponse.Badge> badges = new ArrayList<>();
        if (writtenPostCount >= FIRST_POST_MIN_COUNT) {
            badges.add(new CommunityProfileResponse.Badge("FIRST_POST", "첫 글"));
        }
        if (receivedBookmarkCount >= POPULAR_AUTHOR_MIN_BOOKMARK_COUNT) {
            badges.add(new CommunityProfileResponse.Badge("POPULAR_AUTHOR", "인기 작성자"));
        }
        return List.copyOf(badges);
    }

    private CommunityProfileResponse.MajorDepartment toDepartment(Department department) {
        if (department == null) {
            return null;
        }
        College college = department.getCollege();
        return new CommunityProfileResponse.MajorDepartment(
                department.getId(),
                department.getName(),
                new CommunityProfileResponse.College(college.getId(), college.getName())
        );
    }

    private Map<Long, Long> countMap(List<PostCountProjection> counts) {
        Map<Long, Long> result = new HashMap<>();
        for (PostCountProjection count : counts) {
            result.put(count.getPostId(), count.getCount());
        }
        return result;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPostQueryException();
        }
    }
}
