package com.sebu.backend.crawling.adapter.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.crawling.adapter.parser.ProfessorHomepageUrlNormalizer;
import com.sebu.backend.crawling.adapter.parser.SejongStandardProfessorPageParser;
import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SejongMainProfessorPageAdapterTest {
    private final SejongMainProfessorPageAdapter adapter = new SejongMainProfessorPageAdapter(new ObjectMapper());

    @Test
    void resolvesOnlyFixedOriginAndEncodesPageParameters() {
        Document page = page();
        page.getElementById("selectOption").val("A&B");
        assertThat(adapter.apiUrl(page)).isEqualTo("https://www.sejong.ac.kr/professor/getProfessorListKor.do?siteId=kor&menuCd=3204&deptNo=2135&orderOption=D&selectOption=A%26B");
    }

    @Test
    void rejectsMissingOrInjectedDepartmentCode() {
        var page = page();
        page.getElementById("paramsDeptNo").val("2135&deptNo=9999");
        assertThatThrownBy(() -> adapter.apiUrl(page)).hasMessage("SEJONG_MAIN_DEPARTMENT_PARAMETERS_MISSING");
    }

    @Test
    void adaptsOnlyProfessorFieldsForTheExistingParser() {
        var page = adapter.populate(page(), """
            {"items":[{"korNm":"홍길동","workGdNm":"교수","email":"HONG@example.com",
            "resFld":"언어학, <b>3D 영상</b>","homepage1":"https://example.com/lab",
            "homepage2":"https://example.com/portal","roomNo":"101","campInPhone":"02-123-4567",
            "finalDegree":"박사"},{"korNm":"Jane Smith","resFld":null,"email":null}]}
            """);
        var data = new SejongStandardProfessorPageParser(new ProfessorHomepageUrlNormalizer())
            .parse(new FetchedProfessorPage(page.outerHtml(), page.location()));
        assertThat(data).hasSize(2);
        assertThat(data.getFirst().professorName()).isEqualTo("홍길동");
        assertThat(data.getFirst().email()).isEqualTo("hong@example.com");
        assertThat(data.getFirst().researchIntroduction()).isEqualTo("언어학, 3D 영상");
        assertThat(data.getFirst().homepageUrl()).isEqualTo("https://example.com/lab");
        assertThat(data.getFirst().laboratoryName()).isNull();
        assertThat(data.getLast().researchIntroduction()).isNull();
        assertThat(data.getLast().email()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"items\":[]}", "{\"items\":{}}"})
    void rejectsMissingOrEmptyListsInsteadOfStalingExistingProfessors(String json) {
        assertThatThrownBy(() -> adapter.populate(page(), json))
            .hasMessage("SEJONG_MAIN_PROFESSOR_LIST_EMPTY_OR_MISSING");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> adapter.populate(page(), "<html>Error</html>"))
            .hasMessage("SEJONG_MAIN_INVALID_JSON");
    }

    @Test
    void rejectsNumericResearchFieldInsteadOfCoercingItToText() {
        assertThatThrownBy(() -> adapter.populate(page(), "{\"items\":[{\"korNm\":\"홍길동\",\"resFld\":123}]}"))
            .hasMessage("SEJONG_MAIN_INVALID_FIELD_TYPE: resFld");
    }

    @Test
    void rejectsMissingNames() {
        assertThatThrownBy(() -> adapter.populate(page(), "{\"items\":[{\"email\":\"a@example.com\"}]}"))
            .hasMessage("SEJONG_MAIN_PROFESSOR_NAME_MISSING");
    }

    private Document page() {
        return Jsoup.parse("""
            <script>var CMS={"menuCd":"3204"};</script>
            <input id="paramsDeptNo" value="2135"><input id="orderOption" value="D">
            <input id="selectOption" value=""><p id="proCount"></p><ul id="proShow"></ul>
            """, "https://www.sejong.ac.kr/kor/college/example.do");
    }
}
