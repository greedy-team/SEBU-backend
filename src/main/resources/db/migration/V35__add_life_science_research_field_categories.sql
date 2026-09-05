-- Add only categories not represented by the existing search taxonomy.
-- Existing category IDs, names and mappings are preserved.
INSERT INTO research_field_category (code, name, description, display_order)
SELECT 'FOOD_NUTRITION', '식품·영양', '식품공학·가공·안전·물성, 영양·장 건강, 기능성 식품·소재, 대체식품 및 사료', 22
WHERE NOT EXISTS (
    SELECT 1 FROM research_field_category WHERE code = 'FOOD_NUTRITION'
);

INSERT INTO research_field_category (code, name, description, display_order)
SELECT 'PLANT_AGRICULTURE', '식물·농업생명과학', '식물 생리·면역·유전·육종, 작물·화훼·곤충 등 농생명자원, 농업생명공학 및 스마트농업', 23
WHERE NOT EXISTS (
    SELECT 1 FROM research_field_category WHERE code = 'PLANT_AGRICULTURE'
);

INSERT INTO research_field_category (code, name, description, display_order)
SELECT 'MOLECULAR_BIOTECH', '분자·세포생물학·생명공학', '분자·세포·미생물생물학, 유전·후성유전·오믹스, 단백질·효소·대사, 합성생물학 및 생물공정', 24
WHERE NOT EXISTS (
    SELECT 1 FROM research_field_category WHERE code = 'MOLECULAR_BIOTECH'
);
