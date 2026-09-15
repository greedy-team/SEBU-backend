package com.sebu.backend.community.comment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.community.comment.domain.CommunityComment;
import com.sebu.backend.community.comment.repository.CommunityCommentRepository;
import com.sebu.backend.community.common.CommunityAuthorAssembler;
import com.sebu.backend.community.common.CommunityAuthorMapper;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.global.auth.CurrentUserProvider;
import com.sebu.backend.user.domain.AppUser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CommunityCommentQueryServiceTest {
    @ParameterizedTest
    @CsvSource({
            "1, 1, true, true", "2, 1, false, true", ", 1, false, true",
            "1, 2, false, false", "2, 2, true, false", ", 2, false, false"
    })
    void postAuthorBadgeIsIndependentOfViewerAndConsistentAcrossResponses(
            Long viewerId, long commentAuthorId, boolean mine, boolean isPostAuthor) {
        var posts = mock(CommunityPostRepository.class);
        var comments = mock(CommunityCommentRepository.class);
        var viewer = mock(CurrentUserProvider.class);
        var authors = mock(CommunityAuthorAssembler.class);
        var service = new CommunityCommentQueryService(posts, comments, viewer, authors);
        AppUser postAuthor = mock(AppUser.class);
        AppUser commentAuthor = mock(AppUser.class);
        when(postAuthor.getId()).thenReturn(1L);
        when(commentAuthor.getId()).thenReturn(commentAuthorId);
        var post = new CommunityPost(postAuthor, CommunityPostCategory.FREE, "게시글", "본문");
        var comment = new CommunityComment(post, commentAuthor, "댓글");
        var authorResponse = new CommunityAuthorMapper().toResponse(commentAuthor, null);
        when(posts.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(post));
        when(viewer.currentUserId()).thenReturn(Optional.ofNullable(viewerId));
        when(comments.findByPost_IdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(10L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(comment)));
        when(authors.toResponses(List.of(commentAuthor))).thenReturn(Map.of(commentAuthorId, authorResponse));
        when(authors.toResponse(commentAuthor)).thenReturn(authorResponse);

        var item = service.findComments(10L, 0, 20).comments().getFirst();
        assertThat(item.mine()).isEqualTo(mine);
        assertThat(item.isPostAuthor()).isEqualTo(isPostAuthor);
        var json = new ObjectMapper().valueToTree(item);
        assertThat(json.get("isPostAuthor").asBoolean()).isEqualTo(isPostAuthor);
        assertThat(json.path("author").get("id").isNull()).isTrue();

        // The comment creation service uses this same response builder with the author's ID.
        var created = service.toItem(comment, commentAuthorId);
        assertThat(created.mine()).isTrue();
        assertThat(created.isPostAuthor()).isEqualTo(isPostAuthor);
        assertThat(created.author().id()).isNull();
    }
}
