package com.sebu.backend.community.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.community.bookmark.domain.CommunityPostBookmark;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.comment.domain.CommunityComment;
import com.sebu.backend.community.comment.repository.CommunityCommentRepository;
import com.sebu.backend.community.like.domain.CommunityPostLike;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.GpaBand;
import com.sebu.backend.user.domain.Nickname;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static com.sebu.backend.support.CookieApiRequests.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static com.sebu.backend.support.CookieApiRequests.patch;
import static com.sebu.backend.support.CookieApiRequests.post;
import static com.sebu.backend.support.CookieApiRequests.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommunityApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AppUserRepository appUserRepository;
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired CommunityPostRepository postRepository;
    @Autowired CommunityCommentRepository commentRepository;
    @Autowired CommunityPostLikeRepository likeRepository;
    @Autowired CommunityPostBookmarkRepository bookmarkRepository;

    @Test
    void anonymousUsersCanReadEveryPublicCommunityEndpoint() throws Exception {
        AppUser author = userWithNickname("public-author", "공개작성자", "공개닉네임");
        CommunityPost post = savePost(author, CommunityPostCategory.FREE, "공개 게시글", "공개 본문");
        commentRepository.saveAndFlush(new CommunityComment(post, author, "공개 댓글"));

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/posts/{postId}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.liked").value(false))
                .andExpect(jsonPath("$.data.post.bookmarked").value(false))
                .andExpect(jsonPath("$.data.post.mine").value(false));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments[0].mine").value(false));

        mockMvc.perform(get("/api/v1/users/{userId}/community-profile", author.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.userId").value(author.getId()));
    }

    @Test
    void anonymousUsersCannotCallAnyCommunityMutationEndpoint() throws Exception {
        AppUser author = userWithNickname("unauthorized-author", "인증작성자", "인증닉네임");
        CommunityPost existingPost = savePost(
                author,
                CommunityPostCategory.FREE,
                "인증 필요 게시글",
                "인증 필요 본문"
        );
        CommunityComment comment = commentRepository.saveAndFlush(
                new CommunityComment(existingPost, author, "인증 필요 댓글")
        );

        String postBody = postBody("FREE", "로그인이 필요한 제목", "로그인이 필요한 본문");
        String commentBody = commentBody("로그인이 필요한 댓글");
        RequestBuilder[] requests = {
                post("/api/v1/posts").contentType(MediaType.APPLICATION_JSON).content(postBody),
                put("/api/v1/posts/{postId}", existingPost.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(postBody),
                delete("/api/v1/posts/{postId}", existingPost.getId()),
                post("/api/v1/posts/{postId}/comments", existingPost.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(commentBody),
                patch("/api/v1/posts/{postId}/comments/{commentId}", existingPost.getId(), comment.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(commentBody),
                delete("/api/v1/posts/{postId}/comments/{commentId}", existingPost.getId(), comment.getId()),
                put("/api/v1/posts/{postId}/likes", existingPost.getId()),
                delete("/api/v1/posts/{postId}/likes", existingPost.getId()),
                put("/api/v1/posts/{postId}/bookmarks", existingPost.getId()),
                delete("/api/v1/posts/{postId}/bookmarks", existingPost.getId())
        };

        for (RequestBuilder request : requests) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("ACCESS_TOKEN_INVALID"));
        }
    }

    @Test
    void ownerCanCreateReadUpdateAndSoftDeletePost() throws Exception {
        AppUser owner = userWithNickname("crud-owner", "CRUD실명", "CRUD작성자");

        MvcResult created = mockMvc.perform(post("/api/v1/posts")
                        .with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("QUESTION", "  최초 제목  ", "  최초 본문  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postId").isNumber())
                .andReturn();
        long postId = dataId(created, "postId");

        mockMvc.perform(get("/api/v1/posts/{postId}", postId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.category").value("QUESTION"))
                .andExpect(jsonPath("$.data.post.title").value("최초 제목"))
                .andExpect(jsonPath("$.data.post.content").value("최초 본문"))
                .andExpect(jsonPath("$.data.post.mine").value(true))
                .andExpect(jsonPath("$.data.post.viewCount").value(1));

        mockMvc.perform(put("/api/v1/posts/{postId}", postId)
                        .with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("FREE", "수정된 제목", "수정된 본문")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.category").value("FREE"))
                .andExpect(jsonPath("$.data.post.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.post.content").value("수정된 본문"));

        mockMvc.perform(delete("/api/v1/posts/{postId}", postId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(postId));

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
    }

    @Test
    void nonOwnerCannotUpdateOrDeletePost() throws Exception {
        AppUser owner = userWithNickname("post-owner", "게시글소유자실명", "게시글소유자");
        AppUser other = userWithNickname("post-other", "게시글타인실명", "게시글타인");
        CommunityPost post = savePost(owner, CommunityPostCategory.FREE, "소유권 글", "소유권 본문");

        mockMvc.perform(put("/api/v1/posts/{postId}", post.getId())
                        .with(as(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("FREE", "권한 없는 수정", "권한 없는 본문")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/posts/{postId}", post.getId()).with(as(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("POST_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/posts/{postId}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.title").value("소유권 글"));
    }

    @Test
    void listsAllPostsAndSupportsCategoryAndTitleSearch() throws Exception {
        AppUser author = userWithNickname("filter-author", "필터실명", "필터작성자");
        savePost(author, CommunityPostCategory.FREE, "자유로운 이야기", "자유 본문");
        savePost(author, CommunityPostCategory.FREE, "인건비 자유 토론", "인건비 자유 본문");
        CommunityPost question = savePost(
                author,
                CommunityPostCategory.QUESTION,
                "학부연구생 인건비 질문",
                "인건비 질문 본문"
        );

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.posts.length()").value(3));

        mockMvc.perform(get("/api/v1/posts").param("category", "FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.posts[0].category").value("FREE"))
                .andExpect(jsonPath("$.data.posts[1].category").value("FREE"));

        mockMvc.perform(get("/api/v1/posts")
                        .param("keyword", "인건비")
                        .param("category", "QUESTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.posts[0].id").value(question.getId()))
                .andExpect(jsonPath("$.data.posts[0].title").value("학부연구생 인건비 질문"));
    }

    @Test
    void emptySearchReturnsStableEmptyPage() throws Exception {
        AppUser author = userWithNickname("empty-author", "빈결과실명", "빈결과작성자");
        savePost(author, CommunityPostCategory.FREE, "존재하는 글", "존재하는 본문");

        mockMvc.perform(get("/api/v1/posts").param("keyword", "검색결과없음"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts").isArray())
                .andExpect(jsonPath("$.data.posts").isEmpty())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void invalidPostListQueryParametersReturnInvalidQueryParameter() throws Exception {
        RequestBuilder[] requests = {
                get("/api/v1/posts").param("category", "INVALID"),
                get("/api/v1/posts").param("sort", "INVALID"),
                get("/api/v1/posts").param("page", "-1"),
                get("/api/v1/posts").param("size", "0"),
                get("/api/v1/posts").param("size", "51")
        };

        for (RequestBuilder request : requests) {
            mockMvc.perform(request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("INVALID_QUERY_PARAMETER"));
        }
    }

    @Test
    void invalidPostJsonAndConstraintViolationsReturnValidationError() throws Exception {
        AppUser author = userWithNickname("validation-author", "검증실명", "검증작성자");
        String[] invalidBodies = {
                "{\"category\":\"INVALID\",\"title\":\"제목\",\"content\":\"본문\"}",
                postBody("FREE", "   ", "본문"),
                postBody("FREE", "제목", "   "),
                postBody("FREE", "가".repeat(101), "본문"),
                postBody("FREE", "제목", "가".repeat(2001))
        };

        for (String invalidBody : invalidBodies) {
            mockMvc.perform(post("/api/v1/posts")
                            .with(as(author))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void disallowedCommunityContentReturnsFieldSpecificPolicyError() throws Exception {
        AppUser author = userWithNickname("policy-author", "정책실명", "정책작성자");

        mockMvc.perform(post("/api/v1/posts")
                        .with(as(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("FREE", "차 단.테-스 트 표현", "정상 본문")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CONTENT_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("title"));
    }

    @Test
    void missingAndSoftDeletedCommunityResourcesReturnSpecifiedNotFoundCodes() throws Exception {
        AppUser owner = userWithNickname("not-found-owner", "없음실명", "없음작성자");
        CommunityPost activePost = savePost(
                owner,
                CommunityPostCategory.FREE,
                "리소스 오류 계약 글",
                "리소스 오류 계약 본문"
        );
        CommunityPost deletedPost = savePost(
                owner,
                CommunityPostCategory.FREE,
                "삭제된 리소스 글",
                "삭제된 리소스 본문"
        );
        deletedPost.softDelete();
        postRepository.flush();

        CommunityComment deletedComment = commentRepository.saveAndFlush(
                new CommunityComment(activePost, owner, "삭제될 댓글")
        );
        deletedComment.softDelete();
        commentRepository.flush();

        long missingId = Long.MAX_VALUE;
        RequestBuilder[] missingPostRequests = {
                get("/api/v1/posts/{postId}", missingId),
                get("/api/v1/posts/{postId}", deletedPost.getId()),
                get("/api/v1/posts/{postId}/comments", deletedPost.getId()),
                put("/api/v1/posts/{postId}", missingId)
                        .with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody("FREE", "없는 글 수정", "없는 글 본문"))
        };

        for (RequestBuilder request : missingPostRequests) {
            mockMvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
        }

        RequestBuilder[] missingCommentRequests = {
                patch("/api/v1/posts/{postId}/comments/{commentId}", activePost.getId(), missingId)
                        .with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("없는 댓글 수정")),
                patch(
                                "/api/v1/posts/{postId}/comments/{commentId}",
                                activePost.getId(),
                                deletedComment.getId()
                        )
                        .with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("삭제 댓글 수정"))
        };

        for (RequestBuilder request : missingCommentRequests) {
            mockMvc.perform(request)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
        }
    }

    @Test
    void titleSearchTreatsSqlWildcardCharactersAsLiteralText() throws Exception {
        AppUser author = userWithNickname("wildcard-author", "와일드카드실명", "와일드카드작성자");
        CommunityPost percentPost = savePost(
                author,
                CommunityPostCategory.QUESTION,
                "참여율 100% 질문",
                "퍼센트 본문"
        );
        savePost(author, CommunityPostCategory.QUESTION, "참여율 1000 질문", "숫자 본문");

        mockMvc.perform(get("/api/v1/posts").param("keyword", "100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.posts[0].id").value(percentPost.getId()));
    }

    @Test
    void latestSortIsDeterministicAndPopularSortReturnsTopFourByBookmarkCount() throws Exception {
        AppUser author = userWithNickname("sort-author", "정렬실명", "정렬작성자");
        List<CommunityPost> posts = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            posts.add(savePost(
                    author,
                    index % 2 == 0 ? CommunityPostCategory.FREE : CommunityPostCategory.QUESTION,
                    "정렬 게시글 " + index,
                    "정렬 본문 " + index
            ));
        }

        List<AppUser> bookmarkers = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            bookmarkers.add(userWithNickname(
                    "bookmarker-" + index,
                    "북마커실명" + index,
                    "북마커" + index
            ));
        }
        for (int postIndex = 0; postIndex < posts.size(); postIndex++) {
            for (int userIndex = 0; userIndex < postIndex; userIndex++) {
                bookmarkRepository.save(new CommunityPostBookmark(
                        bookmarkers.get(userIndex),
                        posts.get(postIndex)
                ));
            }
        }
        bookmarkRepository.flush();

        mockMvc.perform(get("/api/v1/posts").param("sort", "LATEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts[0].id").value(posts.get(5).getId()));

        mockMvc.perform(get("/api/v1/posts")
                        .param("sort", "POPULAR")
                        .param("page", "0")
                        .param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts.length()").value(4))
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.posts[0].id").value(posts.get(5).getId()))
                .andExpect(jsonPath("$.data.posts[1].id").value(posts.get(4).getId()))
                .andExpect(jsonPath("$.data.posts[2].id").value(posts.get(3).getId()))
                .andExpect(jsonPath("$.data.posts[3].id").value(posts.get(2).getId()))
                .andExpect(jsonPath("$.data.posts[0].badges", hasItem("HOT")))
                .andExpect(jsonPath("$.data.posts[3].badges", hasItem("HOT")));
    }

    @Test
    void commentCrudMaintainsCountAndEnforcesOwnership() throws Exception {
        AppUser postAuthor = userWithNickname("comment-post-author", "댓글글실명", "댓글글작성자");
        AppUser commenter = userWithNickname("comment-owner", "댓글작성실명", "댓글작성자");
        AppUser other = userWithNickname("comment-other", "댓글타인실명", "댓글타인");
        CommunityPost post = savePost(
                postAuthor,
                CommunityPostCategory.QUESTION,
                "댓글 테스트 글",
                "댓글 테스트 본문"
        );

        MvcResult created = mockMvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .with(as(commenter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("  첫 댓글  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.comment.content").value("첫 댓글"))
                .andExpect(jsonPath("$.data.comment.mine").value(true))
                .andExpect(jsonPath("$.data.commentCount").value(1))
                .andReturn();
        long commentId = nestedDataId(created, "comment", "id");

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.comments[0].mine").value(false));

        mockMvc.perform(patch("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), commentId)
                        .with(as(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("타인의 수정")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMENT_FORBIDDEN"));

        mockMvc.perform(patch("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), commentId)
                        .with(as(commenter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commentBody("수정된 댓글")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value(commentId))
                .andExpect(jsonPath("$.data.content").value("수정된 댓글"));

        mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), commentId)
                        .with(as(commenter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentId").value(commentId))
                .andExpect(jsonPath("$.data.commentCount").value(0));

        mockMvc.perform(get("/api/v1/posts/{postId}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.commentCount").value(0));

        mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}", post.getId(), commentId)
                        .with(as(commenter)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void likeAndBookmarkEndpointsAreIdempotent() throws Exception {
        AppUser author = userWithNickname("reaction-author", "반응글실명", "반응글작성자");
        AppUser reactor = userWithNickname("reactor", "반응자실명", "반응자");
        CommunityPost post = savePost(author, CommunityPostCategory.FREE, "반응 글", "반응 본문");

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(put("/api/v1/posts/{postId}/likes", post.getId()).with(as(reactor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.liked").value(true))
                    .andExpect(jsonPath("$.data.likeCount").value(1));

            mockMvc.perform(put("/api/v1/posts/{postId}/bookmarks", post.getId()).with(as(reactor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.bookmarked").value(true));
        }

        mockMvc.perform(get("/api/v1/posts/{postId}", post.getId()).with(as(reactor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.liked").value(true))
                .andExpect(jsonPath("$.data.post.likeCount").value(1))
                .andExpect(jsonPath("$.data.post.bookmarked").value(true));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/posts/{postId}/likes", post.getId()).with(as(reactor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.liked").value(false))
                    .andExpect(jsonPath("$.data.likeCount").value(0));

            mockMvc.perform(delete("/api/v1/posts/{postId}/bookmarks", post.getId()).with(as(reactor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.bookmarked").value(false));
        }

        mockMvc.perform(get("/api/v1/posts/{postId}", post.getId()).with(as(reactor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.liked").value(false))
                .andExpect(jsonPath("$.data.post.likeCount").value(0))
                .andExpect(jsonPath("$.data.post.bookmarked").value(false));
    }

    @Test
    void withdrawnUserReactionsAreExcludedFromCountsAndPopularity() throws Exception {
        AppUser author = userWithNickname("withdrawn-reaction-author", "탈퇴반응글실명", "탈퇴반응글작성자");
        AppUser withdrawnUser = userWithNickname("withdrawn-reactor", "탈퇴반응실명", "탈퇴반응자");
        AppUser activeUser = userWithNickname("active-reactor", "활성반응실명", "활성반응자");
        CommunityPost withdrawnReactionPost = savePost(
                author,
                CommunityPostCategory.FREE,
                "탈퇴 반응만 있는 글",
                "탈퇴 반응 본문"
        );
        CommunityPost activeReactionPost = savePost(
                author,
                CommunityPostCategory.FREE,
                "활성 반응이 있는 글",
                "활성 반응 본문"
        );
        likeRepository.save(new CommunityPostLike(withdrawnUser, withdrawnReactionPost));
        bookmarkRepository.save(new CommunityPostBookmark(withdrawnUser, withdrawnReactionPost));
        bookmarkRepository.save(new CommunityPostBookmark(activeUser, activeReactionPost));
        withdrawnUser.withdraw();
        appUserRepository.flush();
        likeRepository.flush();
        bookmarkRepository.flush();

        mockMvc.perform(get("/api/v1/posts/{postId}", withdrawnReactionPost.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.post.likeCount").value(0));

        mockMvc.perform(get("/api/v1/posts")
                        .param("sort", "POPULAR")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts[0].id").value(activeReactionPost.getId()));
    }

    @Test
    void publicProfileCalculatesOnlyActiveActivityAndNeverExposesRealName() throws Exception {
        String realName = "절대노출금지실명";
        AppUser profileOwner = userWithNickname("profile-owner", realName, "프로필닉네임");
        AppUser other = userWithNickname("profile-other", "프로필타인실명", "프로필타인");
        AppUser reactor = userWithNickname("profile-reactor", "프로필반응실명", "프로필반응자");

        CommunityPost firstActive = savePost(
                profileOwner,
                CommunityPostCategory.FREE,
                "프로필 활성 글 1",
                "프로필 활성 본문 1"
        );
        CommunityPost secondActive = savePost(
                profileOwner,
                CommunityPostCategory.QUESTION,
                "프로필 활성 글 2",
                "프로필 활성 본문 2"
        );
        CommunityPost deletedOwn = savePost(
                profileOwner,
                CommunityPostCategory.FREE,
                "프로필 삭제 글",
                "프로필 삭제 본문"
        );
        deletedOwn.softDelete();

        CommunityPost otherActive = savePost(
                other,
                CommunityPostCategory.FREE,
                "타인 활성 글",
                "타인 활성 본문"
        );
        CommunityPost otherDeleted = savePost(
                other,
                CommunityPostCategory.FREE,
                "타인 삭제 글",
                "타인 삭제 본문"
        );
        otherDeleted.softDelete();

        likeRepository.save(new CommunityPostLike(reactor, firstActive));
        likeRepository.save(new CommunityPostLike(reactor, secondActive));
        likeRepository.save(new CommunityPostLike(reactor, deletedOwn));

        commentRepository.save(new CommunityComment(otherActive, profileOwner, "활성 작성 댓글"));
        CommunityComment deletedComment = commentRepository.save(
                new CommunityComment(otherActive, profileOwner, "삭제 작성 댓글")
        );
        deletedComment.softDelete();
        commentRepository.save(new CommunityComment(otherDeleted, profileOwner, "삭제 글의 댓글"));
        postRepository.flush();
        likeRepository.flush();
        commentRepository.flush();

        mockMvc.perform(get("/api/v1/users/{userId}/community-profile", profileOwner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.userId").value(profileOwner.getId()))
                .andExpect(jsonPath("$.data.profile.nickname").value("프로필닉네임"))
                .andExpect(jsonPath("$.data.profile.name").doesNotExist())
                .andExpect(jsonPath("$.data.stats.writtenPostCount").value(2))
                .andExpect(jsonPath("$.data.profile.badges[0].code").value("FIRST_POST"))
                .andExpect(jsonPath("$.data.profile.badges[0].label").value("첫 글"))
                .andExpect(jsonPath("$.data.stats.receivedLikeCount").value(2))
                .andExpect(jsonPath("$.data.stats.writtenCommentCount").value(1))
                .andExpect(jsonPath("$.data.posts.totalElements").value(2))
                .andExpect(jsonPath("$.data.posts.items.length()").value(2))
                .andExpect(content().string(not(containsString(realName))));
    }

    @Test
    void publicProfileAwardsPopularAuthorAtFiveActiveReceivedBookmarks() throws Exception {
        AppUser profileOwner = userWithNickname(
                "badge-profile-owner",
                "뱃지프로필실명",
                "뱃지프로필"
        );
        CommunityPost post = savePost(
                profileOwner,
                CommunityPostCategory.FREE,
                "인기 작성자 뱃지 글",
                "인기 작성자 뱃지 본문"
        );
        for (int index = 0; index < 5; index++) {
            AppUser bookmarker = userWithNickname(
                    "badge-bookmarker-" + index,
                    "뱃지북마커실명" + index,
                    "뱃지북마커" + index
            );
            bookmarkRepository.save(new CommunityPostBookmark(bookmarker, post));
        }
        bookmarkRepository.flush();

        mockMvc.perform(get("/api/v1/users/{userId}/community-profile", profileOwner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.badges.length()").value(2))
                .andExpect(jsonPath("$.data.profile.badges[0].code").value("FIRST_POST"))
                .andExpect(jsonPath("$.data.profile.badges[1].code").value("POPULAR_AUTHOR"))
                .andExpect(jsonPath("$.data.profile.badges[1].label").value("인기 작성자"));
    }

    @Test
    void missingNicknameUsesAnonymousFallbackAcrossPostCommentAndProfile() throws Exception {
        String realName = "익명사용자실명";
        AppUser anonymousAuthor = userWithoutNickname("anonymous-author", realName);
        CommunityPost post = savePost(
                anonymousAuthor,
                CommunityPostCategory.FREE,
                "익명 작성 글",
                "익명 작성 본문"
        );
        commentRepository.saveAndFlush(new CommunityComment(post, anonymousAuthor, "익명 작성 댓글"));

        mockMvc.perform(get("/api/v1/posts").param("keyword", "익명 작성 글"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts[0].author.id").value(anonymousAuthor.getId()))
                .andExpect(jsonPath("$.data.posts[0].author.nickname").value("익명"))
                .andExpect(content().string(not(containsString(realName))));

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments[0].author.id").value(anonymousAuthor.getId()))
                .andExpect(jsonPath("$.data.comments[0].author.nickname").value("익명"))
                .andExpect(content().string(not(containsString(realName))));

        mockMvc.perform(get("/api/v1/users/{userId}/community-profile", anonymousAuthor.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.nickname").value("익명"))
                .andExpect(jsonPath("$.data.profile.name").doesNotExist())
                .andExpect(content().string(not(containsString(realName))));
    }

    private AppUser userWithNickname(String key, String realName, String nickname) {
        Department department = department(key);
        LocalDateTime now = LocalDateTime.now();
        AppUser user = AppUser.sejong(
                key,
                realName,
                department.getName(),
                department,
                now.minusMinutes(1)
        );
        user.updateProfile(
                Nickname.from(nickname),
                (short) 3,
                GpaBand.GTE_3_5,
                "공개 자기소개 " + key,
                now,
                "v1",
                "test"
        );
        return appUserRepository.saveAndFlush(user);
    }

    private AppUser userWithoutNickname(String key, String realName) {
        Department department = department(key);
        LocalDateTime now = LocalDateTime.now();
        AppUser user = AppUser.sejong(
                key,
                realName,
                department.getName(),
                department,
                now.minusMinutes(1)
        );
        user.updateGrade(3, now);
        return appUserRepository.saveAndFlush(user);
    }

    private Department department(String key) {
        College college = collegeRepository.save(new College("커뮤니티대학-" + key));
        return departmentRepository.save(new Department(college, "커뮤니티학과-" + key));
    }

    private CommunityPost savePost(
            AppUser author,
            CommunityPostCategory category,
            String title,
            String body
    ) {
        return postRepository.saveAndFlush(new CommunityPost(author, category, title, body));
    }

    private RequestPostProcessor as(AppUser user) {
        return jwt().jwt(token -> token
                .subject(user.getId().toString())
                .claim("role", "USER"));
    }

    private String postBody(String category, String title, String body) throws Exception {
        return objectMapper.writeValueAsString(new PostRequest(category, title, body));
    }

    private String commentBody(String body) throws Exception {
        return objectMapper.writeValueAsString(new CommentRequest(body));
    }

    private long dataId(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return root.path("data").path(field).asLong();
    }

    private long nestedDataId(MvcResult result, String object, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return root.path("data").path(object).path(field).asLong();
    }

    private record PostRequest(String category, String title, String content) {
    }

    private record CommentRequest(String content) {
    }
}
