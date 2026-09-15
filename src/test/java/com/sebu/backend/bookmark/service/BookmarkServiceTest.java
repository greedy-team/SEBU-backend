package com.sebu.backend.bookmark.service;

import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.domain.BookmarkId;
import com.sebu.backend.bookmark.dto.BookmarkedLaboratoriesResponse;
import com.sebu.backend.bookmark.repository.BookmarkRepository;
import com.sebu.backend.college.domain.College;
import com.sebu.backend.college.repository.CollegeRepository;
import com.sebu.backend.department.domain.Department;
import com.sebu.backend.department.repository.DepartmentRepository;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratory.exception.LaboratoryNotFoundException;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.laboratory.service.LaboratoryManagementService;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class BookmarkServiceTest {
    @Autowired
    CollegeRepository collegeRepository;
    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    ProfessorRepository professorRepository;
    @Autowired
    AppUserRepository appUserRepository;
    @Autowired
    LaboratoryManagementService laboratoryManagementService;
    @Autowired
    BookmarkService bookmarkService;
    @Autowired
    LaboratoryRepository laboratoryRepository;
    @Autowired
    BookmarkRepository bookmarkRepository;
    @Autowired
    EntityManager entityManager;
    @Test
    void softDeletedLaboratoryIsNotBookmarkable() {
        College college = collegeRepository.save(new College("삭제연구실북마크테스트대학"));
        Department department = departmentRepository.save(new Department(college, "전자공학과"));
        Professor professor = professorRepository.save(new Professor(department, "이교수", null));
        Laboratory laboratory = laboratoryManagementService.create(
                professor.getId(), department.getId(), "전자 연구실", null, RecruitmentStatus.CLOSED
        );
        AppUser user = appUserRepository.save(new AppUser("student@example.com"));

        laboratoryManagementService.softDelete(laboratory.getId());

        assertThat(laboratory.isDeleted()).isTrue();
        assertThatThrownBy(() -> bookmarkService.add(user.getId(), laboratory.getId()))
                .isInstanceOf(LaboratoryNotFoundException.class)
                .hasMessage("LABORATORY_NOT_FOUND");
    }

    @Test
    void softDeletedLaboratoryIsExcludedFromBookmarks() {
        College college = collegeRepository.save(new College("북마크조회대학"));
        Department department = departmentRepository.save(
                new Department(college, "북마크조회학과")
        );
        Professor professor = professorRepository.save(
                new Professor(department, "북마크조회교수", null)
        );
        Laboratory laboratory = laboratoryRepository.save(
                new Laboratory(
                        professor,
                        department,
                        "삭제된 북마크 연구실",
                        null,
                        RecruitmentStatus.CLOSED
                )
        );
        AppUser user = appUserRepository.save(
                new AppUser("deleted-laboratory-bookmark@example.com")
        );
        bookmarkRepository.save(new Bookmark(user, laboratory));
        laboratory.softDelete();
        entityManager.flush();
        entityManager.clear();

        BookmarkedLaboratoriesResponse result =
                bookmarkService.getBookmarkedLaboratories(user.getId());

        assertThat(result.items()).isEmpty();
    }

    @Test
    void softDeletedLaboratoryBookmarkCanBeRemoved() {
        College college = collegeRepository.save(new College("삭제북마크대학"));
        Department department = departmentRepository.save(
                new Department(college, "삭제북마크학과")
        );
        Professor professor = professorRepository.save(
                new Professor(department, "삭제북마크교수", null)
        );
        Laboratory laboratory = laboratoryRepository.save(
                new Laboratory(
                        professor,
                        department,
                        "삭제 후 북마크 해제 연구실",
                        null,
                        RecruitmentStatus.CLOSED
                )
        );
        AppUser user = appUserRepository.save(
                new AppUser("remove-deleted-laboratory-bookmark@example.com")
        );
        bookmarkRepository.save(new Bookmark(user, laboratory));
        laboratory.softDelete();
        entityManager.flush();
        entityManager.clear();

        bookmarkService.remove(user.getId(), laboratory.getId());

        assertThat(bookmarkRepository.existsById(
                new BookmarkId(user.getId(), laboratory.getId())
        )).isFalse();
    }

    @Test
    void 북마크가_없으면_빈_목록을_반환한다() {
        AppUser user = appUserRepository.save(
                new AppUser("bookmark-test@example.com")
        );

        BookmarkedLaboratoriesResponse result =
                bookmarkService.getBookmarkedLaboratories(user.getId());

        assertThat(result.items()).isEmpty();
    }

    @Test
    void 북마크한_연구실을_전체_반환한다() {

        // given
        AppUser user = appUserRepository.save(
                new AppUser("bookmark-page-test@example.com")
        );

        College college = collegeRepository.save(
                new College("소프트웨어융합대학")
        );

        Department department = departmentRepository.save(
                new Department(college, "컴퓨터공학과")
        );

        Professor professor = professorRepository.save(
                new Professor(department, "홍교수", null)
        );

        Laboratory lab1 = laboratoryRepository.save(
                new Laboratory(
                        professor,
                        department,
                        "AI 연구실",
                        null,
                        RecruitmentStatus.RECRUITING
                )
        );

        Laboratory lab2 = laboratoryRepository.save(
                new Laboratory(
                        professor,
                        department,
                        "데이터 연구실",
                        null,
                        RecruitmentStatus.RECRUITING
                )
        );

        Laboratory lab3 = laboratoryRepository.save(
                new Laboratory(
                        professor,
                        department,
                        "비전 연구실",
                        null,
                        RecruitmentStatus.RECRUITING
                )
        );

        bookmarkRepository.save(new Bookmark(user, lab1));
        bookmarkRepository.save(new Bookmark(user, lab2));
        bookmarkRepository.save(new Bookmark(user, lab3));

        // when
        BookmarkedLaboratoriesResponse result =
                bookmarkService.getBookmarkedLaboratories(user.getId());

        // then
        assertThat(result.items()).hasSize(3);
    }

    @Test
    void 북마크를_저장할_수_있다() {
        // given
        College college = collegeRepository.save(new College("저장테스트대학"));
        Department department =
                departmentRepository.save(new Department(college, "저장테스트학과"));
        Professor professor =
                professorRepository.save(new Professor(department, "저장테스트교수", null));

        Laboratory laboratory = laboratoryRepository.save(
                new Laboratory(
                        professor,
                        department,
                        "저장테스트 연구실",
                        null,
                        RecruitmentStatus.RECRUITING
                )
        );

        AppUser user = appUserRepository.save(
                new AppUser("bookmark-save@example.com")
        );

        // when
        bookmarkService.add(user.getId(), laboratory.getId());

        // then
        BookmarkId bookmarkId =
                new BookmarkId(user.getId(), laboratory.getId());

        assertThat(bookmarkRepository.existsById(bookmarkId))
                .isTrue();
    }
    @Test
    void 이미_북마크한_랩실을_다시_저장해도_중복_저장되지_않는다() {
        // given
        College college = collegeRepository.save(new College("중복테스트대학"));
        Department department =
                departmentRepository.save(new Department(college, "중복테스트학과"));
        Professor professor =
                professorRepository.save(new Professor(department, "중복테스트교수", null));

        Laboratory laboratory = laboratoryRepository.save(
                new Laboratory(
                        professor,
                        department,
                        "중복테스트 연구실",
                        null,
                        RecruitmentStatus.RECRUITING
                )
        );

        AppUser user = appUserRepository.save(
                new AppUser("bookmark-duplicate@example.com")
        );

        // when
        bookmarkService.add(user.getId(), laboratory.getId());
        bookmarkService.add(user.getId(), laboratory.getId());

        // then
        assertThat(
                bookmarkRepository.countByUser_IdAndLaboratory_DeletedAtIsNull(
                        user.getId()
                )
        ).isEqualTo(1);
    }

    @Test
    void 북마크를_삭제할_수_있다() {
        // given
        College college =
                collegeRepository.save(new College("삭제테스트대학"));

        Department department =
                departmentRepository.save(
                        new Department(college, "삭제테스트학과")
                );

        Professor professor =
                professorRepository.save(
                        new Professor(department, "삭제테스트교수", null)
                );

        Laboratory laboratory =
                laboratoryRepository.save(
                        new Laboratory(
                                professor,
                                department,
                                "삭제테스트 연구실",
                                null,
                                RecruitmentStatus.RECRUITING
                        )
                );

        AppUser user =
                appUserRepository.save(
                        new AppUser("bookmark-delete@example.com")
                );

        bookmarkService.add(user.getId(), laboratory.getId());

        BookmarkId bookmarkId =
                new BookmarkId(user.getId(), laboratory.getId());

        assertThat(bookmarkRepository.existsById(bookmarkId))
                .isTrue();

        // when
        bookmarkService.remove(
                user.getId(),
                laboratory.getId()
        );

        // then
        assertThat(bookmarkRepository.existsById(bookmarkId))
                .isFalse();
    }
    @Test
    void 존재하지_않는_북마크를_삭제해도_예외가_발생하지_않는다() {
        College college =
                collegeRepository.save(new College("삭제멱등대학"));

        Department department =
                departmentRepository.save(
                        new Department(college, "삭제멱등학과")
                );

        Professor professor =
                professorRepository.save(
                        new Professor(department, "삭제멱등교수", null)
                );

        Laboratory laboratory =
                laboratoryRepository.save(
                        new Laboratory(
                                professor,
                                department,
                                "삭제멱등 연구실",
                                null,
                                RecruitmentStatus.RECRUITING
                        )
                );

        AppUser user =
                appUserRepository.save(
                        new AppUser("bookmark-delete-empty@example.com")
                );

        // 북마크를 저장하지 않고 바로 삭제
        bookmarkService.remove(
                user.getId(),
                laboratory.getId()
        );

        BookmarkId bookmarkId =
                new BookmarkId(user.getId(), laboratory.getId());

        assertThat(bookmarkRepository.existsById(bookmarkId))
                .isFalse();
    }

}
