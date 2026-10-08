package com.sebu.backend.mypage.service;

import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.community.bookmark.domain.CommunityPostBookmark;
import com.sebu.backend.community.bookmark.repository.CommunityPostBookmarkRepository;
import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import com.sebu.backend.community.post.repository.CommunityPostRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MyPageQueryCountIntegrationTest {

    @Autowired MyPageService myPageService;
    @Autowired AppUserRepository appUserRepository;
    @Autowired CollegeRepository collegeRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ProfessorRepository professorRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired BookmarkRepository bookmarkRepository;
    @Autowired CommunityPostRepository communityPostRepository;
    @Autowired CommunityPostBookmarkRepository communityPostBookmarkRepository;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void 북마크가_증가해도_마이페이지_조회_쿼리_수는_일정하다() {
        AppUser userWithOneBookmark =
                appUserRepository.save(new AppUser("mypage-query-one@example.com"));
        AppUser userWithThreeBookmarks =
                appUserRepository.save(new AppUser("mypage-query-three@example.com"));

        College college = collegeRepository.save(new College("쿼리수대학"));
        Department department = departmentRepository.save(
                new Department(college, "쿼리수학과")
        );
        Professor professor = professorRepository.save(
                new Professor(department, "쿼리수교수", null)
        );

        List<Laboratory> laboratories = laboratoryRepository.saveAll(List.of(
                laboratory(professor, department, "쿼리수 연구실 1"),
                laboratory(professor, department, "쿼리수 연구실 2"),
                laboratory(professor, department, "쿼리수 연구실 3"),
                laboratory(professor, department, "쿼리수 연구실 4")
        ));

        bookmarkRepository.save(new Bookmark(userWithOneBookmark, laboratories.get(0)));
        bookmarkRepository.saveAll(List.of(
                new Bookmark(userWithThreeBookmarks, laboratories.get(1)),
                new Bookmark(userWithThreeBookmarks, laboratories.get(2)),
                new Bookmark(userWithThreeBookmarks, laboratories.get(3))
        ));
        entityManager.flush();

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        long oneBookmarkQueryCount = queryCount(
                statistics,
                userWithOneBookmark.getId(),
                1
        );
        long threeBookmarksQueryCount = queryCount(
                statistics,
                userWithThreeBookmarks.getId(),
                3
        );

        assertThat(threeBookmarksQueryCount)
                .isEqualTo(oneBookmarkQueryCount)
                .isLessThanOrEqualTo(6L);
    }

    @Test
    void 게시글_북마크가_증가해도_마이페이지_조회_쿼리_수는_일정하다() {
        AppUser author = appUserRepository.save(
                new AppUser("mypage-post-query-author@example.com")
        );
        AppUser userWithOneBookmark = appUserRepository.save(
                new AppUser("mypage-post-query-one@example.com")
        );
        AppUser userWithThreeBookmarks = appUserRepository.save(
                new AppUser("mypage-post-query-three@example.com")
        );

        List<CommunityPost> posts = communityPostRepository.saveAll(List.of(
                post(author, "쿼리수 게시글 1"),
                post(author, "쿼리수 게시글 2"),
                post(author, "쿼리수 게시글 3"),
                post(author, "쿼리수 게시글 4")
        ));
        communityPostBookmarkRepository.save(
                new CommunityPostBookmark(userWithOneBookmark, posts.get(0))
        );
        communityPostBookmarkRepository.saveAll(List.of(
                new CommunityPostBookmark(userWithThreeBookmarks, posts.get(1)),
                new CommunityPostBookmark(userWithThreeBookmarks, posts.get(2)),
                new CommunityPostBookmark(userWithThreeBookmarks, posts.get(3))
        ));
        entityManager.flush();

        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        long oneBookmarkQueryCount = postQueryCount(
                statistics,
                userWithOneBookmark.getId(),
                1
        );
        long threeBookmarksQueryCount = postQueryCount(
                statistics,
                userWithThreeBookmarks.getId(),
                3
        );

        assertThat(threeBookmarksQueryCount)
                .isEqualTo(oneBookmarkQueryCount)
                .isLessThanOrEqualTo(6L);
    }

    private long queryCount(Statistics statistics, Long userId, int expectedBookmarks) {
        entityManager.clear();
        statistics.clear();

        assertThat(myPageService.getMyPage(userId).bookmarkedLaboratories().items())
                .hasSize(expectedBookmarks);

        return statistics.getPrepareStatementCount();
    }

    private long postQueryCount(Statistics statistics, Long userId, int expectedBookmarks) {
        entityManager.clear();
        statistics.clear();

        assertThat(myPageService.getMyPage(userId).bookmarkedPosts().items())
                .hasSize(expectedBookmarks);

        return statistics.getPrepareStatementCount();
    }

    private CommunityPost post(AppUser author, String title) {
        return new CommunityPost(
                author,
                CommunityPostCategory.FREE,
                title,
                title + " 내용"
        );
    }

    private Laboratory laboratory(
            Professor professor,
            Department department,
            String name
    ) {
        return new Laboratory(
                professor,
                department,
                name,
                null,
                RecruitmentStatus.RECRUITING
        );
    }
}
