package com.sebu.backend.crawling.adapter.fetcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SejongMainProfessorPageAdapter {
    private static final Pattern MENU = Pattern.compile("\"menuCd\"\\s*:\\s*\"([0-9]+)\"");
    private final ObjectMapper objectMapper;

    public String apiUrl(Document page) {
        String department = parameter(page, "paramsDeptNo");
        var menu = MENU.matcher(page.select("script").html());
        if (!department.matches("[0-9]+") || !menu.find()) {
            throw new ProfessorCrawlException("SEJONG_MAIN_DEPARTMENT_PARAMETERS_MISSING");
        }
        return "https://www.sejong.ac.kr/professor/getProfessorListKor.do?siteId=kor&menuCd="
            + menu.group(1) + "&deptNo=" + department
            + "&orderOption=" + encoded(parameter(page, "orderOption"))
            + "&selectOption=" + encoded(parameter(page, "selectOption"));
    }

    public Document populate(Document page, String json) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new ProfessorCrawlException("SEJONG_MAIN_INVALID_JSON", exception);
        }
        JsonNode items = root == null ? null : root.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            throw new ProfessorCrawlException("SEJONG_MAIN_PROFESSOR_LIST_EMPTY_OR_MISSING");
        }
        Element list = page.selectFirst("#proShow");
        if (list == null) {
            throw new ProfessorCrawlException("SEJONG_MAIN_PROFESSOR_CONTAINER_MISSING");
        }
        list.empty();
        for (JsonNode item : items) {
            String name = text(item, "korNm");
            if (name.isBlank() || name.equals("-")) {
                throw new ProfessorCrawlException("SEJONG_MAIN_PROFESSOR_NAME_MISSING");
            }
            Element card = list.appendElement("li");
            Element profile = card.appendElement("div").addClass("b-professor-name");
            profile.appendElement("p").text(name);
            profile.appendElement("ul").appendElement("li").appendElement("span").text(text(item, "workGdNm"));
            card.appendElement("div").addClass("b-professor-info")
                .appendElement("span").addClass("b-email").text(text(item, "email"));
            card.appendElement("div").addClass("b-professor-field")
                .appendElement("p").text(text(item, "resFld"));
            String homepage = text(item, "homepage1");
            if (!homepage.isBlank() && !homepage.equals("#none") && !homepage.equals("-")) {
                card.appendElement("div").addClass("b-professor-link")
                    .appendElement("a").addClass("homepage").attr("href", homepage);
            }
        }
        Element count = page.selectFirst("#proCount");
        if (count != null) count.text("총 " + items.size() + "명 등록되었습니다.");
        return page;
    }

    private String parameter(Document page, String id) {
        Element input = page.getElementById(id);
        return input == null ? "" : input.val().trim();
    }

    private String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || value.isNull()) return "";
        if (!value.isTextual()) {
            throw new ProfessorCrawlException("SEJONG_MAIN_INVALID_FIELD_TYPE: " + field);
        }
        return Jsoup.parseBodyFragment(value.textValue()).text().replace('\u00a0', ' ').trim();
    }
}
