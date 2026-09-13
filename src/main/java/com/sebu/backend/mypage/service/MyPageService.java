package com.sebu.backend.mypage.service;

import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.community.bookmark.domain.CommunityPostBookmark;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.comment.repository.CommunityCommentRepository;
import com.sebu.backend.community.common.CommunityAuthorMapper;
import com.sebu.backend.community.common.repository.PostCountProjection;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.laboratory.dto.LaboratoriesResult;
import com.sebu.backend.laboratory.query.LaboratorySummaryAssembler;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldProjection;
import com.sebu.backend.laboratory.repository.LaboratoryResearchFieldRepository;
import com.sebu.backend.laboratory.repository.LaboratorySummaryProjection;
import com.sebu.backend.mypage.dto.MyPageResponse;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.exception.UserNotFoundException;
import com.sebu.backend.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final AppUserRepository appUserRepository;
    private final BookmarkRepository bookmarkRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryResearchFieldRepository laboratoryResearchFieldRepository;
    private final LaboratorySummaryAssembler laboratorySummaryAssembler;
    private final CommunityPostBookmarkRepository communityPostBookmarkRepository;
    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityAuthorMapper communityAuthorMapper;

    public MyPageResponse getMyPage(Long userId) {

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        long bookmarkedLaboratoryCount =
                bookmarkRepository.countByUser_IdAndLaboratory_DeletedAtIsNull(userId);

        List<Bookmark> bookmarks =
                bookmarkRepository.findBookmarkedLaboratories(userId);

        List<CommunityPostBookmark> postBookmarks =
                communityPostBookmarkRepository
                        .findByUser_IdAndPost_DeletedAtIsNullOrderByCreatedAtDescPost_IdDesc(userId);

        List<Long> laboratoryIds = bookmarks.stream()
                .map(bookmark -> bookmark.getLaboratory().getId())
                .toList();

        Map<Long, LaboratorySummaryProjection> summaryByLaboratoryId =
                laboratoryIds.isEmpty()
                        ? Map.of()
                        : laboratoryRepository.findSummariesByIds(
                                userId,
                                laboratoryIds
                        ).stream()
                        .collect(Collectors.toMap(
                                LaboratorySummaryProjection::getId,
                                summary -> summary
                        ));

        Map<Long, List<String>> researchFieldsByLaboratoryId =
                laboratoryIds.isEmpty()
                        ? Map.of()
                        : laboratoryResearchFieldRepository
                        .findFieldsByLaboratoryIds(laboratoryIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                LaboratoryResearchFieldProjection::getLaboratoryId,
                                Collectors.mapping(
                                        LaboratoryResearchFieldProjection::getName,
                                        Collectors.toList()
                                )
                        ));

        MyPageResponse.Profile profile = toProfile(user);

        MyPageResponse.Summary summary =
                new MyPageResponse.Summary(
                        bookmarkedLaboratoryCount,
                        postBookmarks.size()
                );

        List<MyPageResponse.BookmarkedLaboratory> bookmarkedLaboratories =
                bookmarks.stream()
                        .map(bookmark ->
                                toBookmarkedLaboratory(
                                        bookmark,
                                        summaryByLaboratoryId,
                                        researchFieldsByLaboratoryId
                                )
                        )
                        .toList();

        List<Long> postIds = postBookmarks.stream()
                .map(bookmark -> bookmark.getPost().getId())
                .toList();
        Map<Long, Long> likeCounts = postIds.isEmpty()
                ? Map.of()
                : countMap(communityPostLikeRepository.countByPostIds(postIds));
        Map<Long, Long> commentCounts = postIds.isEmpty()
                ? Map.of()
                : countMap(communityCommentRepository.countActiveByPostIds(postIds));

        List<MyPageResponse.BookmarkedPost> bookmarkedPosts = postBookmarks.stream()
                .map(bookmark -> toBookmarkedPost(
                        bookmark,
                        likeCounts,
                        commentCounts
                ))
                .toList();

        return new MyPageResponse(
                profile,
                summary,
                new MyPageResponse.BookmarkedLaboratories(
                        bookmarkedLaboratories
                ),
                new MyPageResponse.BookmarkedPosts(
                        bookmarkedPosts
                )
        );
    }

    private MyPageResponse.Profile toProfile(AppUser user) {
        MyPageResponse.DepartmentSummary department = null;

        if (user.getMajorDepartment() != null
                && user.getMajorDepartment().getName().equals(user.getSejongDepartmentName())) {
            department = new MyPageResponse.DepartmentSummary(
                    user.getMajorDepartment().getId().toString(),
                    user.getMajorDepartment().getName()
            );
        } else if (user.getSejongDepartmentName() != null) {
            department = new MyPageResponse.DepartmentSummary(
                    null,
                    user.getSejongDepartmentName()
            );
        }

        return new MyPageResponse.Profile(
                user.getName(),
                user.getNickname(),
                user.getGrade(),
                department,
                user.getAcademicField(),
                user.getGpaBand(),
                user.getIntroduction(),
                user.isProfileCompleted(),
                user.getProfileUpdatedAt()
        );
    }

    private MyPageResponse.BookmarkedLaboratory toBookmarkedLaboratory(
            Bookmark bookmark,
            Map<Long, LaboratorySummaryProjection> summaryByLaboratoryId,
            Map<Long, List<String>> researchFieldsByLaboratoryId
    ) {
        Long laboratoryId = bookmark.getLaboratory().getId();
        LaboratorySummaryProjection projection =
                summaryByLaboratoryId.get(laboratoryId);

        if (projection == null) {
            throw new IllegalStateException("LABORATORY_SUMMARY_NOT_FOUND");
        }

        LaboratoriesResult.LaboratoryResult laboratory =
                laboratorySummaryAssembler.assemble(
                        projection,
                        researchFieldsByLaboratoryId.getOrDefault(
                                laboratoryId,
                                List.of()
                        )
                );

        return new MyPageResponse.BookmarkedLaboratory(
                bookmark.getCreatedAt(),
                toLaboratorySummary(laboratory)
        );
    }

    private MyPageResponse.LaboratorySummary toLaboratorySummary(
            LaboratoriesResult.LaboratoryResult laboratory
    ) {
        MyPageResponse.CollegeSummary collegeSummary =
                new MyPageResponse.CollegeSummary(
                        laboratory.college().id().toString(),
                        laboratory.college().name()
                );

        MyPageResponse.DepartmentSummary departmentSummary =
                new MyPageResponse.DepartmentSummary(
                        laboratory.department().id().toString(),
                        laboratory.department().name()
                );
        MyPageResponse.ProfessorSummary professorSummary =
                new MyPageResponse.ProfessorSummary(
                        laboratory.professor().id().toString(),
                        laboratory.professor().name()
                );

        return new MyPageResponse.LaboratorySummary(
                laboratory.id().toString(),
                laboratory.name(),
                laboratory.websiteUrl(),
                collegeSummary,
                departmentSummary,
                professorSummary,
                laboratory.researchFields(),
                laboratory.recruitmentStatus().name(),
                laboratory.bookmarkCount(),
                laboratory.bookmarked()
        );

    }

    private MyPageResponse.BookmarkedPost toBookmarkedPost(
            CommunityPostBookmark bookmark,
            Map<Long, Long> likeCounts,
            Map<Long, Long> commentCounts
    ) {
        CommunityPost post = bookmark.getPost();

        return new MyPageResponse.BookmarkedPost(
                bookmark.getCreatedAt(),
                new MyPageResponse.PostSummary(
                        post.getId(),
                        post.getCategory(),
                        post.getTitle(),
                        communityAuthorMapper.toResponse(post.getAuthor()),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        commentCounts.getOrDefault(post.getId(), 0L),
                        post.getViewCount(),
                        post.getCreatedAt()
                )
        );
    }

    private Map<Long, Long> countMap(List<PostCountProjection> counts) {
        Map<Long, Long> result = new HashMap<>();
        for (PostCountProjection count : counts) {
            result.put(count.getPostId(), count.getCount());
        }
        return result;
    }
}
