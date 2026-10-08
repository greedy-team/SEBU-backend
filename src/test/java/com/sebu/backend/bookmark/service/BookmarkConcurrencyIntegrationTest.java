package com.sebu.backend.bookmark.service;

import com.sebu.backend.bookmark.domain.BookmarkLimitPolicy;
import com.sebu.backend.bookmark.domain.BookmarkType;
import com.sebu.backend.bookmark.exception.BookmarkLimitExceededException;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.community.reaction.dto.PostBookmarkResponse;
import com.sebu.backend.community.reaction.service.CommunityPostReactionService;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BookmarkConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    @Autowired
    BookmarkService bookmarkService;
    @Autowired
    CommunityPostReactionService postReactionService;
    @Autowired
    CommunityPostRepository postRepository;
    @Autowired
    CollegeRepository collegeRepository;
    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    ProfessorRepository professorRepository;
    @Autowired
    LaboratoryRepository laboratoryRepository;
    @MockitoSpyBean
    AppUserRepository appUserRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add(
                "spring.datasource.hikari.transaction-isolation",
                () -> "TRANSACTION_REPEATABLE_READ"
        );
    }

    @Test
    void simultaneousBookmarkRequestsSucceedWithOneBookmark() throws Exception {
        TestFixture fixture = createFixture();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> addBookmark = () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            bookmarkService.add(fixture.userId(), fixture.laboratoryId());
            return null;
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Void>> futures = List.of(
                    executor.submit(addBookmark),
                    executor.submit(addBookmark)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            futures.get(0).get(10, TimeUnit.SECONDS);
            futures.get(1).get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        Integer bookmarkCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM bookmark
                WHERE user_id = ?
                  AND laboratory_id = ?
                """,
                Integer.class,
                fixture.userId(),
                fixture.laboratoryId()
        );

        assertThat(bookmarkCount).isOne();
    }

    @Test
    void concurrentPostBookmarksAtFortyNineCannotExceedFifty() throws Exception {
        PostBookmarkLimitFixture fixture = createPostBookmarkLimitFixture();
        assertThat(countActivePostBookmarks(fixture.userId()))
                .isEqualTo(BookmarkLimitPolicy.MAX_BOOKMARKS_PER_TYPE - 1);
        CountDownLatch bothReachedUserLock = new CountDownLatch(2);
        CountDownLatch releaseUserLock = new CountDownLatch(1);
        Answer<?> repositoryDelegate = mockingDetails(appUserRepository)
                .getMockCreationSettings()
                .getDefaultAnswer();

        // Align both transactions immediately before the user lock to make RR snapshot regressions deterministic.
        doAnswer(invocation -> {
            bothReachedUserLock.countDown();
            if (!releaseUserLock.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("POST_BOOKMARK_USER_LOCK_NOT_RELEASED");
            }
            return repositoryDelegate.answer(invocation);
        }).when(appUserRepository).findByIdForUpdate(fixture.userId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Object> results;
        try {
            Future<Object> first = executor.submit(() -> capturePostBookmarkResult(
                    fixture.userId(),
                    fixture.firstTargetPostId()
            ));
            Future<Object> second = executor.submit(() -> capturePostBookmarkResult(
                    fixture.userId(),
                    fixture.secondTargetPostId()
            ));

            assertThat(bothReachedUserLock.await(10, TimeUnit.SECONDS)).isTrue();
            releaseUserLock.countDown();
            results = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
        } finally {
            releaseUserLock.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        List<PostBookmarkResponse> successes = results.stream()
                .filter(PostBookmarkResponse.class::isInstance)
                .map(PostBookmarkResponse.class::cast)
                .toList();
        List<BookmarkLimitExceededException> failures = results.stream()
                .filter(BookmarkLimitExceededException.class::isInstance)
                .map(BookmarkLimitExceededException.class::cast)
                .toList();

        assertThat(successes).singleElement()
                .satisfies(response -> assertThat(response.bookmarked()).isTrue());
        assertThat(failures).singleElement()
                .satisfies(exception -> {
                    assertThat(exception.bookmarkType()).isEqualTo(BookmarkType.POST);
                    assertThat(exception.limit()).isEqualTo(BookmarkLimitPolicy.MAX_BOOKMARKS_PER_TYPE);
                });
        assertThat(countActivePostBookmarks(fixture.userId()))
                .isEqualTo(BookmarkLimitPolicy.MAX_BOOKMARKS_PER_TYPE);
        assertThat(countTargetPostBookmarks(fixture)).isOne();
    }

    private Object capturePostBookmarkResult(Long userId, Long postId) {
        try {
            return postReactionService.bookmark(userId, postId);
        } catch (BookmarkLimitExceededException exception) {
            return exception;
        }
    }

    private PostBookmarkLimitFixture createPostBookmarkLimitFixture() {
        String suffix = UUID.randomUUID().toString();
        AppUser user = appUserRepository.saveAndFlush(
                new AppUser("post-bookmark-limit-" + suffix + "@example.com")
        );
        int limit = Math.toIntExact(BookmarkLimitPolicy.MAX_BOOKMARKS_PER_TYPE);
        List<CommunityPost> posts = new ArrayList<>(limit + 1);

        for (int index = 0; index < limit + 1; index++) {
            posts.add(new CommunityPost(
                    user,
                    CommunityPostCategory.FREE,
                    "동시 북마크 제한 게시글-" + suffix + "-" + index,
                    "동시 북마크 제한 본문-" + index
            ));
        }
        List<CommunityPost> savedPosts = postRepository.saveAllAndFlush(posts);
        List<Object[]> existingBookmarks = new ArrayList<>(limit - 1);
        for (int index = 0; index < limit - 1; index++) {
            existingBookmarks.add(new Object[]{user.getId(), savedPosts.get(index).getId()});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO community_post_bookmark (user_id, post_id) VALUES (?, ?)",
                existingBookmarks
        );

        return new PostBookmarkLimitFixture(
                user.getId(),
                savedPosts.get(limit - 1).getId(),
                savedPosts.get(limit).getId()
        );
    }

    private long countActivePostBookmarks(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM community_post_bookmark bookmark
                JOIN community_post post ON post.id = bookmark.post_id
                WHERE bookmark.user_id = ?
                  AND post.deleted_at IS NULL
                """,
                Long.class,
                userId
        );
        return count == null ? 0 : count;
    }

    private long countTargetPostBookmarks(PostBookmarkLimitFixture fixture) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM community_post_bookmark
                WHERE user_id = ?
                  AND post_id IN (?, ?)
                """,
                Long.class,
                fixture.userId(),
                fixture.firstTargetPostId(),
                fixture.secondTargetPostId()
        );
        return count == null ? 0 : count;
    }

    private TestFixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        College college = collegeRepository.save(
                new College("동시 북마크 대학-" + suffix)
        );
        Department department = departmentRepository.save(
                new Department(college, "동시 북마크 학과-" + suffix)
        );
        Professor professor = professorRepository.save(
                new Professor(department, "동시 북마크 교수-" + suffix, null)
        );
        Laboratory laboratory = laboratoryRepository.saveAndFlush(
                new Laboratory(
                        professor,
                        department,
                        "동시 북마크 연구실-" + suffix,
                        null,
                        RecruitmentStatus.RECRUITING
                )
        );
        AppUser user = appUserRepository.saveAndFlush(
                new AppUser("bookmark-" + suffix + "@example.com")
        );

        return new TestFixture(laboratory.getId(), user.getId());
    }

    private record TestFixture(Long laboratoryId, Long userId) {
    }

    private record PostBookmarkLimitFixture(
            Long userId,
            Long firstTargetPostId,
            Long secondTargetPostId
    ) {
    }
}
