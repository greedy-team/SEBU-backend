package com.sebu.backend.community.repository;

import com.sebu.backend.community.bookmark.domain.CommunityPostBookmark;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.comment.domain.CommunityComment;
import com.sebu.backend.community.comment.repository.CommunityCommentRepository;
import com.sebu.backend.community.like.domain.CommunityPostLike;
import com.sebu.backend.community.like.repository.CommunityPostLikeRepository;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CommunitySchemaIntegrationTest {
    @Autowired AppUserRepository appUserRepository;
    @Autowired CommunityPostRepository postRepository;
    @Autowired CommunityCommentRepository commentRepository;
    @Autowired CommunityPostLikeRepository likeRepository;
    @Autowired CommunityPostBookmarkRepository bookmarkRepository;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void communityEntitiesAreMappedToTheirTables() {
        AppUser author = appUserRepository.saveAndFlush(new AppUser("community-author@example.com"));
        AppUser reader = appUserRepository.saveAndFlush(new AppUser("community-reader@example.com"));
        CommunityPost post = postRepository.saveAndFlush(
                new CommunityPost(author, CommunityPostCategory.FREE, "첫 게시글", "게시글 내용")
        );
        CommunityComment comment = commentRepository.saveAndFlush(
                new CommunityComment(post, reader, "첫 댓글")
        );
        likeRepository.saveAndFlush(new CommunityPostLike(reader, post));
        bookmarkRepository.saveAndFlush(new CommunityPostBookmark(reader, post));
        entityManager.clear();

        CommunityPost foundPost = postRepository.findById(post.getId()).orElseThrow();
        CommunityComment foundComment = commentRepository.findById(comment.getId()).orElseThrow();

        assertThat(foundPost.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(foundPost.getCategory()).isEqualTo(CommunityPostCategory.FREE);
        assertThat(foundPost.getViewCount()).isZero();
        assertThat(foundPost.getCreatedAt()).isNotNull();
        assertThat(foundComment.getPost().getId()).isEqualTo(post.getId());
        assertThat(foundComment.getAuthor().getId()).isEqualTo(reader.getId());
        assertThat(likeRepository.existsById(new com.sebu.backend.community.like.domain.CommunityPostLikeId(reader.getId(), post.getId()))).isTrue();
        assertThat(bookmarkRepository.existsById(new com.sebu.backend.community.bookmark.domain.CommunityPostBookmarkId(reader.getId(), post.getId()))).isTrue();
    }

    @Test
    void duplicateLikeAndBookmarkAreRejectedByCompositePrimaryKeys() {
        AppUser user = appUserRepository.saveAndFlush(new AppUser("community-unique@example.com"));
        CommunityPost post = postRepository.saveAndFlush(
                new CommunityPost(user, CommunityPostCategory.QUESTION, "질문", "질문 내용")
        );

        jdbcTemplate.update(
                "INSERT INTO community_post_like (user_id, post_id) VALUES (?, ?)",
                user.getId(), post.getId()
        );
        jdbcTemplate.update(
                "INSERT INTO community_post_bookmark (user_id, post_id) VALUES (?, ?)",
                user.getId(), post.getId()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO community_post_like (user_id, post_id) VALUES (?, ?)",
                user.getId(), post.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO community_post_bookmark (user_id, post_id) VALUES (?, ?)",
                user.getId(), post.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unsupportedCategoryAndNegativeViewCountAreRejected() {
        AppUser author = appUserRepository.saveAndFlush(new AppUser("community-check@example.com"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO community_post (author_id, category, title, content) VALUES (?, ?, ?, ?)",
                author.getId(), "NOTICE", "공지", "내용"
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO community_post (author_id, category, title, content, view_count) VALUES (?, ?, ?, ?, ?)",
                author.getId(), CommunityPostCategory.FREE.name(), "제목", "내용", -1
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void physicallyDeletingPostCascadesToCommentLikeAndBookmark() {
        AppUser user = appUserRepository.saveAndFlush(new AppUser("community-cascade@example.com"));
        CommunityPost post = postRepository.saveAndFlush(
                new CommunityPost(user, CommunityPostCategory.FREE, "삭제할 글", "삭제 테스트")
        );
        commentRepository.saveAndFlush(new CommunityComment(post, user, "삭제될 댓글"));
        likeRepository.saveAndFlush(new CommunityPostLike(user, post));
        bookmarkRepository.saveAndFlush(new CommunityPostBookmark(user, post));
        entityManager.clear();

        jdbcTemplate.update("DELETE FROM community_post WHERE id = ?", post.getId());

        assertThat(count("community_comment", "post_id", post.getId())).isZero();
        assertThat(count("community_post_like", "post_id", post.getId())).isZero();
        assertThat(count("community_post_bookmark", "post_id", post.getId())).isZero();
    }

    @Test
    void popularPostsAreOrderedByBookmarkCount() {
        AppUser author = appUserRepository.saveAndFlush(new AppUser("community-popular-author@example.com"));
        AppUser reader = appUserRepository.saveAndFlush(new AppUser("community-popular-reader@example.com"));
        CommunityPost withoutBookmark = postRepository.saveAndFlush(
                new CommunityPost(author, CommunityPostCategory.FREE, "일반 글", "일반 내용")
        );
        CommunityPost bookmarked = postRepository.saveAndFlush(
                new CommunityPost(author, CommunityPostCategory.FREE, "북마크 글", "북마크 내용")
        );
        bookmarkRepository.saveAndFlush(new CommunityPostBookmark(reader, bookmarked));

        var result = postRepository.findPopular(null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(CommunityPost::getId)
                .containsExactly(bookmarked.getId(), withoutBookmark.getId());
    }

    private long count(String table, String column, Long value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class,
                value
        );
    }
}
