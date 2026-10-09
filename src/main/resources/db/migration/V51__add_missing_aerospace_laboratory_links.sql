-- Official aerospace faculty directories and linked homepages reviewed on 2026-10-09.
-- Keep existing IDs, affiliations and user content. Fill only missing URLs.
-- MANUAL provenance prevents subsequent crawls from replacing these verified links.
-- Match professor identity and an actual common department in the engineering college;
-- a primary or linked department may establish membership on either side.
-- Some official Lab Website links lead to faculty homepages rather than standalone labs.

-- 홍성경 (skhong@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=1793136&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://www.sejong-gnclab.com/125',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '홍성경 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '홍성경'
        AND p.email = 'skhong@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 박성수 (sungsu@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=1793126&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://www.space.re.kr/index.html',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '박성수 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '박성수'
        AND p.email = 'sungsu@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 박병운 (byungwoon@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=1792866&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/byungwoon/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '박병운 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '박병운'
        AND p.email = 'byungwoon@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 이균호 (khlee0406@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=1792916&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/khlee0406/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '이균호 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '이균호'
        AND p.email = 'khlee0406@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 김재강 (jaegkim@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=2403786&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://sites.google.com/view/svdhlab',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '김재강 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '항공시스템공학과')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '김재강'
        AND p.email = 'jaegkim@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 김오종 (ojong@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=1792876&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://prof.sejong.ac.kr/ojong/index.do',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '김오종 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '김오종'
        AND p.email = 'ojong@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 유호준 (hojun.you@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=2403806&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://sites.google.com/view/machlab/home',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '유호준 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '항공시스템공학과')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '유호준'
        AND p.email = 'hojun.you@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 조병운 (bjo@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=1793666&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://sites.google.com/view/sju-issl',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '조병운 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '조병운'
        AND p.email = 'bjo@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 심한슬 (hshim12@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=2016691&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://sites.google.com/view/hshimlab/about',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '심한슬 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '심한슬'
        AND p.email = 'hshim12@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 김정현 (junghyun.kim@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=2403766&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://junghyunandykim.wixsite.com/home',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '김정현 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공', '지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '김정현'
        AND p.email = 'junghyun.kim@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 김인녕 (innyoungkim@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=3930804&page=1&category_idx=82031
UPDATE laboratory
SET website_url = 'https://sites.google.com/view/innyounglab',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '김인녕 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('우주항공공학전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '김인녕'
        AND p.email = 'innyoungkim@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );

-- 강승훈 (shkang@sejong.ac.kr)
-- Source: https://ae.sejong.ac.kr/shop_contents/myboard_read.htm?load_type=&page_idx=0&tag_on=&h_search_c=0&h_search_v=&me_popup=&myboard_code=professor&page_limit=50&idx=3831876&page=1&category_idx=82041
UPDATE laboratory
SET website_url = 'https://sites.google.com/view/shkang',
    website_url_source = 'MANUAL',
    updated_at = CURRENT_TIMESTAMP
WHERE name = '강승훈 교수님 연구실'
  AND deleted_at IS NULL
  AND website_url IS NULL
  AND EXISTS (
      SELECT 1
      FROM professor p
      JOIN department d ON d.name IN ('지능형드론융합전공')
      JOIN college c ON c.id = d.college_id AND c.name = '공과대학'
      WHERE p.id = laboratory.professor_id
        AND p.name = '강승훈'
        AND p.email = 'shkang@sejong.ac.kr'
        AND (
            p.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM professor_department pd
                WHERE pd.professor_id = p.id AND pd.department_id = d.id
            )
        )
        AND (
            laboratory.department_id = d.id
            OR EXISTS (
                SELECT 1 FROM laboratory_department ld
                WHERE ld.laboratory_id = laboratory.id AND ld.department_id = d.id
            )
        )
  );
