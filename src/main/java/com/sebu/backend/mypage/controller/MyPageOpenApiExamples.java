package com.sebu.backend.mypage.controller;

final class MyPageOpenApiExamples {
    private MyPageOpenApiExamples() { }

    static final String PROFILE_REQUEST = """
        {
          "nickname": "세부학생",
          "grade": 3,
          "academicField": "ENGINEERING",
          "gpaBand": "GTE_3_5",
          "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다."
        }
        """;

    static final String PROFILE_RESPONSE = """
        {
          "success": true,
          "data": {
            "name": "홍길동",
            "nickname": "세부학생",
            "grade": 3,
            "department": {
              "id": "12",
              "name": "컴퓨터공학과"
            },
            "academicField": "ENGINEERING",
            "gpaBand": "GTE_3_5",
            "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다.",
            "profileCompleted": true,
            "profileUpdatedAt": "2026-09-12T14:30:00"
          },
          "error": null
        }
        """;

    static final String MYPAGE_RESPONSE = """
        {
          "success": true,
          "data": {
            "profile": {
              "name": "홍길동",
              "nickname": "세부학생",
              "grade": 3,
              "department": {
                "id": "12",
                "name": "컴퓨터공학과"
              },
              "academicField": "ENGINEERING",
              "gpaBand": "GTE_3_5",
              "introduction": "머신러닝과 컴퓨터 비전에 관심이 있습니다.",
              "profileCompleted": true,
              "profileUpdatedAt": "2026-09-12T14:30:00"
            },
            "summary": {
              "bookmarkedLaboratoryCount": 0,
              "bookmarkedPostCount": 0
            },
            "bookmarkedLaboratories": {
              "items": []
            },
            "bookmarkedPosts": {
              "items": []
            }
          },
          "error": null
        }
        """;

    static final String INVALID_ACADEMIC_FIELD = """
        {
          "success": false,
          "data": null,
          "error": {
            "code": "VALIDATION_ERROR",
            "message": "입력값을 확인해 주세요.",
            "fieldErrors": [
              {
                "field": "academicField",
                "reason": "INVALID_VALUE",
                "message": "지원하지 않는 계열입니다. 목록에서 다시 선택해 주세요."
              }
            ],
            "traceId": null
          }
        }
        """;
}
