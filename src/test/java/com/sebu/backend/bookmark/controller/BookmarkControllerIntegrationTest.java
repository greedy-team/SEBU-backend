package com.sebu.backend.bookmark.controller;

import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.domain.BookmarkId;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static com.sebu.backend.support.CookieApiRequests.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookmarkControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    CollegeRepository collegeRepository;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    ProfessorRepository professorRepository;

    @Autowired
    LaboratoryRepository laboratoryRepository;

    @Autowired
    BookmarkRepository bookmarkRepository;

    @Test
    void 로그인한_사용자는_북마크한_랩실을_조회할_수_있다() throws Exception {
        AppUser user = appUserRepository.save(
                new AppUser("bookmark-controller@example.com")
        );

        mockMvc.perform(
                        get("/api/v1/users/me/bookmarked-laboratories")
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        "private, no-store"
                ))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    void 로그인한_사용자는_랩실_북마크를_삭제할_수_있다() throws Exception {
        AppUser user = appUserRepository.save(
                new AppUser("bookmark-delete@example.com")
        );

        College college = collegeRepository.save(
                new College("북마크DELETE대학")
        );

        Department department = departmentRepository.save(
                new Department(college, "북마크DELETE학과")
        );

        Professor professor = professorRepository.save(
                new Professor(department, "북마크DELETE교수", null)
        );

        Laboratory laboratory = laboratoryRepository.save(
                new Laboratory(
                        professor,
                        department,
                        "북마크DELETE 연구실",
                        null,
                        RecruitmentStatus.RECRUITING
                )
        );

        // 미리 북마크 저장
        Bookmark bookmark = new Bookmark(user, laboratory);
        bookmarkRepository.save(bookmark);

        BookmarkId bookmarkId =
                new BookmarkId(user.getId(), laboratory.getId());

        assertThat(bookmarkRepository.existsById(bookmarkId))
                .isTrue();

        // DELETE 요청
        mockMvc.perform(
                        delete(
                                "/api/v1/laboratories/{laboratoryId}/bookmark",
                                laboratory.getId()
                        )
                                .with(jwt().jwt(jwt -> jwt
                                        .subject(user.getId().toString())
                                        .claim("role", "USER")
                                ))
                )
                .andExpect(status().isNoContent());

        // 실제 DB에서도 삭제됐는지 확인
        assertThat(bookmarkRepository.existsById(bookmarkId))
                .isFalse();
    }
}
