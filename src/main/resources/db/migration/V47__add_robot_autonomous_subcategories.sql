-- Existing category IDs and codes stay unchanged. New IDs are assigned by the database.
ALTER TABLE research_field_category
    ADD COLUMN parent_id BIGINT NULL;

ALTER TABLE research_field_category
    ADD CONSTRAINT fk_research_field_category_parent
        FOREIGN KEY (parent_id) REFERENCES research_field_category (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_research_field_category_parent_order
    ON research_field_category (parent_id, display_order);

INSERT INTO research_field_category (code, name, description, display_order, parent_id)
SELECT seed.code, seed.name, seed.description, seed.display_order, parent.id
FROM (
    SELECT 'ROBOT_AUTONOMOUS_ROBOTICS' AS code, '로봇공학·메카트로닉스' AS name, '로봇공학, 로보틱스, 메카트로닉스 및 지능형 로봇' AS description, 33 AS display_order
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_DESIGN', '로봇 설계·메커니즘', '로봇 구조, 메커니즘 및 설계', 34
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_CONTROL', '로봇 제어·매니퓰레이션', '로봇 제어, 조작 및 매니퓰레이터', 35
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_LEARNING', '로봇 학습·지능', '로봇 학습, 작업 지능 및 의사결정', 36
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_PHYSICAL_AI', '피지컬 AI·인간-로봇 상호작용', '피지컬 AI, 체화 지능 및 인간-로봇 상호작용', 37
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_SERVICE', '서비스·휴머노이드 로봇', '서비스 로봇과 휴머노이드 로봇', 38
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_FIELD_ROBOTS', '건설·농업 로봇', '건설, 농업 및 현장 작업 로봇', 39
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_DRIVING', '자율주행·지능형 모빌리티', '자율주행 차량과 지능형 모빌리티', 40
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_PATH_PLANNING', '자율주행 경로·행동 계획', '경로 계획, 행동 계획 및 장애물 회피', 41
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_NAVIGATION', '항법·위치·자세 추정', '항법, 위치 측정, 자세 추정 및 지도 작성', 42
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_PERCEPTION', '환경 인식·객체 탐지', '환경 인식, 객체 탐지 및 상황 이해', 43
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_SENSOR_FUSION', '센서 융합·컴퓨터 비전', '카메라, 레이더, 라이다 센서 융합 및 컴퓨터 비전', 44
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_UAV', '드론·무인항공기', '드론, 무인항공기 및 비행 제어', 45
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_AIR_MOBILITY', '군집·편대 비행·항공 모빌리티', '군집·편대 비행 및 도심 항공 모빌리티', 46
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_USV', '무인수상정·자율운항 선박', '무인수상정과 자율운항 선박', 47
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_UUV', '수중 로봇·무인잠수정', '수중 로봇, 무인잠수정 및 수중운동체', 48
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_UNMANNED', '무인이동체·유무인 복합체계', '무인이동체 및 유무인 복합체계', 49
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_EMBEDDED', '로봇 임베디드·펌웨어', '로봇과 무인이동체의 임베디드 시스템 및 펌웨어', 50
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_INDUSTRIAL', '산업·광산 자율운영', '산업 현장과 광산의 자율운영', 51
    UNION ALL SELECT 'ROBOT_AUTONOMOUS_AUTOMOTIVE', '자동차 제어·운전자 지원', '자동차 제어와 첨단 운전자 지원 시스템', 52
) seed
JOIN research_field_category parent ON parent.code = 'ROBOT_AUTONOMOUS';

-- The reviewed names below come from V25, V36 and V45. A deployment with
-- additional parent mappings fails the guard rather than silently guessing.
CREATE TABLE v47_robot_field_mapping (
    field_name VARCHAR(100) NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    CONSTRAINT pk_v47_robot_field_mapping PRIMARY KEY (field_name)
);

INSERT INTO v47_robot_field_mapping (field_name, category_code) VALUES
    ('로보틱스', 'ROBOT_AUTONOMOUS_ROBOTICS'),
    ('로봇', 'ROBOT_AUTONOMOUS_ROBOTICS'),
    ('로봇공학', 'ROBOT_AUTONOMOUS_ROBOTICS'),
    ('로봇 공학', 'ROBOT_AUTONOMOUS_ROBOTICS'),
    ('메카트로닉스', 'ROBOT_AUTONOMOUS_ROBOTICS'),
    ('지능형 로봇', 'ROBOT_AUTONOMOUS_ROBOTICS'),
    ('인류를 위한 로봇 기술 개발', 'ROBOT_AUTONOMOUS_ROBOTICS'),
    ('AI로봇', 'ROBOT_AUTONOMOUS_ROBOTICS'),
    ('로봇 & 메커니즘 디자인', 'ROBOT_AUTONOMOUS_DESIGN'),
    ('로보틱스용 물리 센서 개발', 'ROBOT_AUTONOMOUS_DESIGN'),
    ('로봇 제어/로봇 매니퓰레이터', 'ROBOT_AUTONOMOUS_CONTROL'),
    ('로봇 조작 및 작업 학습', 'ROBOT_AUTONOMOUS_LEARNING'),
    ('로봇을 위한 인공지능 알고리즘', 'ROBOT_AUTONOMOUS_LEARNING'),
    ('시계열 로봇 센서 데이터 처리를 위한 AI', 'ROBOT_AUTONOMOUS_LEARNING'),
    ('작업 상황 이해 및 추론', 'ROBOT_AUTONOMOUS_LEARNING'),
    ('지능형 이동체 인공지능', 'ROBOT_AUTONOMOUS_LEARNING'),
    ('무인체계 및 지능로봇 분야 피지컬 AI 유무인복합체계 외', 'ROBOT_AUTONOMOUS_PHYSICAL_AI'),
    ('물리적 AI(Embodied AI)', 'ROBOT_AUTONOMOUS_PHYSICAL_AI'),
    ('바이오로보틱스 생체 신호 인간-로봇 상호작용 웨어러블 로보틱스 원격 로봇 제어 시스템', 'ROBOT_AUTONOMOUS_PHYSICAL_AI'),
    ('인간-로봇 상호작용', 'ROBOT_AUTONOMOUS_PHYSICAL_AI'),
    ('극한환경 자율로봇 및 피지컬 AI', 'ROBOT_AUTONOMOUS_PHYSICAL_AI'),
    ('피지컬AI', 'ROBOT_AUTONOMOUS_PHYSICAL_AI'),
    ('휴머노이드 로봇', 'ROBOT_AUTONOMOUS_SERVICE'),
    ('서비스로봇', 'ROBOT_AUTONOMOUS_SERVICE'),
    ('건설작업로봇', 'ROBOT_AUTONOMOUS_FIELD_ROBOTS'),
    ('AI 기반 건설로봇 운영', 'ROBOT_AUTONOMOUS_FIELD_ROBOTS'),
    ('건설로봇', 'ROBOT_AUTONOMOUS_FIELD_ROBOTS'),
    ('농업 로봇 및 AI', 'ROBOT_AUTONOMOUS_FIELD_ROBOTS'),
    ('강화학습기반 자율협력주행시스템', 'ROBOT_AUTONOMOUS_DRIVING'),
    ('자율주행 모빌리티 제어', 'ROBOT_AUTONOMOUS_DRIVING'),
    ('지능형 모빌리티 시스템 및 제어', 'ROBOT_AUTONOMOUS_DRIVING'),
    ('지능형 모빌리티 제어', 'ROBOT_AUTONOMOUS_DRIVING'),
    ('트랙터·트레일러 자율주행', 'ROBOT_AUTONOMOUS_DRIVING'),
    ('경로 계획 및 장애물 회피', 'ROBOT_AUTONOMOUS_PATH_PLANNING'),
    ('및 지도 작성 설명 가능한 강화학습', 'ROBOT_AUTONOMOUS_PATH_PLANNING'),
    ('자율주행 차량 거동계획', 'ROBOT_AUTONOMOUS_PATH_PLANNING'),
    ('지능형 탐색', 'ROBOT_AUTONOMOUS_PATH_PLANNING'),
    ('자율주행차 경로계획', 'ROBOT_AUTONOMOUS_PATH_PLANNING'),
    ('공중 관성항법 시스템', 'ROBOT_AUTONOMOUS_NAVIGATION'),
    ('궤도 추적', 'ROBOT_AUTONOMOUS_NAVIGATION'),
    ('심층 영상/라이다 위치추정 강화학습(XRL)', 'ROBOT_AUTONOMOUS_NAVIGATION'),
    ('자세 추정', 'ROBOT_AUTONOMOUS_NAVIGATION'),
    ('항법', 'ROBOT_AUTONOMOUS_NAVIGATION'),
    ('위성항법', 'ROBOT_AUTONOMOUS_NAVIGATION'),
    ('항법시스템', 'ROBOT_AUTONOMOUS_NAVIGATION'),
    ('보행자 및 차량 객체 인식', 'ROBOT_AUTONOMOUS_PERCEPTION'),
    ('드론 환경 인식', 'ROBOT_AUTONOMOUS_PERCEPTION'),
    ('자율주행자동차 환경 인식', 'ROBOT_AUTONOMOUS_PERCEPTION'),
    ('자율주행을 위한 라이다와 카메라 기반 환경 인지', 'ROBOT_AUTONOMOUS_PERCEPTION'),
    ('카메라·레이더·라이다 센서 융합', 'ROBOT_AUTONOMOUS_SENSOR_FUSION'),
    ('컴퓨터 비전 및 제어 시스템', 'ROBOT_AUTONOMOUS_SENSOR_FUSION'),
    ('드론 펌웨어 개발 및 알고리즘 구현', 'ROBOT_AUTONOMOUS_EMBEDDED'),
    ('수중무인체 등 무인이동체 임베디드시스템 탐지인식 및 AI 의사결정시스템', 'ROBOT_AUTONOMOUS_EMBEDDED'),
    ('무인 항공기(UAV)', 'ROBOT_AUTONOMOUS_UAV'),
    ('AI 기반 무인비행체', 'ROBOT_AUTONOMOUS_UAV'),
    ('무인항공기(UAV)', 'ROBOT_AUTONOMOUS_UAV'),
    ('UAV', 'ROBOT_AUTONOMOUS_UAV'),
    ('MAV', 'ROBOT_AUTONOMOUS_UAV'),
    ('Solar UAV', 'ROBOT_AUTONOMOUS_UAV'),
    ('성층권 HALE/HAPS', 'ROBOT_AUTONOMOUS_AIR_MOBILITY'),
    ('군집·편대 비행', 'ROBOT_AUTONOMOUS_AIR_MOBILITY'),
    ('UAM', 'ROBOT_AUTONOMOUS_AIR_MOBILITY'),
    ('선박 제어 알고리즘', 'ROBOT_AUTONOMOUS_USV'),
    ('선박 충돌회피', 'ROBOT_AUTONOMOUS_USV'),
    ('자율운항선박', 'ROBOT_AUTONOMOUS_USV'),
    ('무인수상정(USV)', 'ROBOT_AUTONOMOUS_USV'),
    ('수중', 'ROBOT_AUTONOMOUS_UUV'),
    ('수중 차량 및 로봇', 'ROBOT_AUTONOMOUS_UUV'),
    ('잠수함 및 수중운동체 자율화', 'ROBOT_AUTONOMOUS_UUV'),
    ('무인잠수정', 'ROBOT_AUTONOMOUS_UUV'),
    ('무인잠수정(AUV)', 'ROBOT_AUTONOMOUS_UUV'),
    ('무인이동체 항법유도제어 센서 융합 및 자율주행항법 드론', 'ROBOT_AUTONOMOUS_UNMANNED'),
    ('지상', 'ROBOT_AUTONOMOUS_UNMANNED'),
    ('해양 무인이동체', 'ROBOT_AUTONOMOUS_UNMANNED'),
    ('AI 에이전트 기반 자율 광산 운영', 'ROBOT_AUTONOMOUS_INDUSTRIAL'),
    ('인공신경망기반 첨단운전자보조시스템', 'ROBOT_AUTONOMOUS_AUTOMOTIVE'),
    ('자동차 제어/군집주행', 'ROBOT_AUTONOMOUS_AUTOMOTIVE'),
    ('자율주행차 제어', 'ROBOT_AUTONOMOUS_AUTOMOTIVE');

CREATE TABLE v47_robot_mapping_guard (
    violations INT NOT NULL,
    CONSTRAINT ck_v47_robot_mapping_complete CHECK (violations = 0)
);

INSERT INTO v47_robot_mapping_guard (violations)
SELECT COUNT(*)
FROM research_field_category_mapping mapping
JOIN research_field_category parent ON parent.id = mapping.category_id
JOIN research_field field ON field.id = mapping.research_field_id
LEFT JOIN v47_robot_field_mapping reassignment ON reassignment.field_name = field.name
WHERE parent.code = 'ROBOT_AUTONOMOUS' AND reassignment.field_name IS NULL;

UPDATE research_field_category_mapping mapping
SET category_id = (
    SELECT child.id
    FROM research_field field
    JOIN v47_robot_field_mapping reassignment ON reassignment.field_name = field.name
    JOIN research_field_category child ON child.code = reassignment.category_code
    WHERE field.id = mapping.research_field_id
)
WHERE mapping.category_id = (
    SELECT parent.id FROM research_field_category parent
    WHERE parent.code = 'ROBOT_AUTONOMOUS'
);

DROP TABLE v47_robot_mapping_guard;
DROP TABLE v47_robot_field_mapping;
