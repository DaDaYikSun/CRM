-- V2__seed_base.sql: 기본 데이터 (FR-DATA-003, DB 설계 8장) (#142)
-- V1(DDL) 안의 INSERT(coat_type 4행, setting 4행, message_template 2행, report_template 1행)는 여기서 다시 넣지 않는다.
-- 시드 계정(staff)은 비밀번호를 환경 변수에서 읽어야 해서 SQL이 아니라 SeedAccountInitializer가 넣는다 (NFR-SEC-001).
-- 머지된 뒤에는 고치지 않는다. 값을 바꾸거나 더하면 번호를 올린 새 파일로 (CLAUDE.md 5번).

SET time_zone = '+09:00';

-- ---------------------------------------------------------------------
-- 1. 기준값: CRM 사용 전 · 후 비교 기준일 (DB 설계 4-8, 더미 데이터 매뉴얼 'CRM 도입일', PR #165)
-- ---------------------------------------------------------------------
INSERT INTO setting (setting_key, setting_value, value_type, description) VALUES
  ('CRM_START_DATE', '2026-06-01', 'DATE', 'CRM 사용 전 · 후 비교 기준일 [FR-DATA-002]');

-- ---------------------------------------------------------------------
-- 2. 품종 선택지 (POL-REG-002, 더미 데이터 매뉴얼 '품종 선택지 20~30개 + 믹스')
-- [제안] 문서에 목록이 없어 #142 PR에서 제안한다 (도메인 전문가 확인 필요).
-- 기본 털 유형(default_coat_type_id)은 정해진 값이 없어 비워 둔다. 등록 화면에서 직접 고른다.
-- ---------------------------------------------------------------------
INSERT INTO breed (species, name, sort_order) VALUES
  ('DOG', '말티즈', 1),
  ('DOG', '푸들', 2),
  ('DOG', '포메라니안', 3),
  ('DOG', '비숑 프리제', 4),
  ('DOG', '시츄', 5),
  ('DOG', '치와와', 6),
  ('DOG', '요크셔 테리어', 7),
  ('DOG', '진돗개', 8),
  ('DOG', '웰시 코기', 9),
  ('DOG', '골든 리트리버', 10),
  ('DOG', '래브라도 리트리버', 11),
  ('DOG', '시바견', 12),
  ('DOG', '닥스훈트', 13),
  ('DOG', '미니어처 슈나우저', 14),
  ('DOG', '비글', 15),
  ('DOG', '프렌치 불독', 16),
  ('DOG', '퍼그', 17),
  ('DOG', '스피츠', 18),
  ('DOG', '페키니즈', 19),
  ('DOG', '사모예드', 20),
  ('DOG', '보더 콜리', 21),
  ('DOG', '셔틀랜드 쉽독', 22),
  ('DOG', '이탈리안 그레이하운드', 23),
  ('DOG', '꼬똥 드 툴레아', 24),
  ('DOG', '베들링턴 테리어', 25),
  ('DOG', '믹스', 99);

-- ---------------------------------------------------------------------
-- 3. 접종 종류 · 주요 접종 (접종 규칙 표 1장, 팀 결정 K9 · #59, DB 설계 4-6)
-- 주요 접종(누락률에 넣음)은 종합백신 · 광견병만 (POL-MISS-004)
-- ---------------------------------------------------------------------
INSERT INTO vaccine_type (code, name, is_major, sort_order) VALUES
  ('DHPPL',     '종합백신',          1, 1),
  ('CORONA',    '코로나 장염',       0, 2),
  ('KENNEL',    '켄넬코프',          0, 3),
  ('RABIES',    '광견병',            1, 4),
  ('INFLUENZA', '인플루엔자',        0, 5),
  ('HEARTWORM', '심장사상충 예방약', 0, 6),
  ('DEWORM',    '구충제',            0, 7);

-- ---------------------------------------------------------------------
-- 4. 접종 규칙 (접종 규칙 표 1장, 구조는 DB 설계 4-7 행 예시, #137)
-- 값이 모두 정해진 종류만 넣는다. 접종 규칙 표 2장 2~5번이 미확정인 종류(종합백신 · 코로나 장염 · 켄넬코프 · 인플루엔자)는
-- 기초접종 간격이 [?]라 넣지 않는다 (DB 설계 4-7: 구현자가 임의 값을 넣지 않는다).
-- 추가접종 행만 넣으면 계산 규칙 1(첫 행 시작 나이 = 첫 예정일)이 첫 예정일을 생후 365일로 잘못 잡으므로 그 종류의 행은 모두 뺐다.
-- 확인 결과가 나오면 번호를 올린 새 마이그레이션 파일로 넣는다 (CLAUDE.md 5번).
-- ---------------------------------------------------------------------
INSERT INTO vaccine_rule (vaccine_type_id, phase, age_from_days, age_to_days, interval_days, dose_count, note)
SELECT vaccine_type_id, 'PRIMARY', 90, 365, 365, 1,
       '생후 3개월 이상 1회. 3개월 미만에 맞혀도 이 행 1회를 채워 12개월 뒤 2차 (접종 규칙 표 1장)'
  FROM vaccine_type WHERE code = 'RABIES';
INSERT INTO vaccine_rule (vaccine_type_id, phase, age_from_days, age_to_days, interval_days, dose_count, note)
SELECT vaccine_type_id, 'BOOSTER', 365, NULL, 365, NULL, '매년 1회 (접종 규칙 표 1장)'
  FROM vaccine_type WHERE code = 'RABIES';

INSERT INTO vaccine_rule (vaccine_type_id, phase, age_from_days, age_to_days, interval_days, dose_count, note)
SELECT vaccine_type_id, 'PREVENTIVE', 56, NULL, 30, NULL, '생후 8주부터 매월 1회, 1년 내내 (접종 규칙 표 1장)'
  FROM vaccine_type WHERE code = 'HEARTWORM';

INSERT INTO vaccine_rule (vaccine_type_id, phase, age_from_days, age_to_days, interval_days, dose_count, note)
SELECT vaccine_type_id, 'PREVENTIVE', 14, 56, 14, NULL,
       '생후 2주부터 2주 간격. 이유 후 2주까지를 생후 8주(56일)까지로 읽음 (팀 결정 K23)'
  FROM vaccine_type WHERE code = 'DEWORM';
INSERT INTO vaccine_rule (vaccine_type_id, phase, age_from_days, age_to_days, interval_days, dose_count, note)
SELECT vaccine_type_id, 'PREVENTIVE', 56, 182, 30, NULL, '6개월까지 매월 (접종 규칙 표 1장)'
  FROM vaccine_type WHERE code = 'DEWORM';
INSERT INTO vaccine_rule (vaccine_type_id, phase, age_from_days, age_to_days, interval_days, dose_count, note)
SELECT vaccine_type_id, 'PREVENTIVE', 182, NULL, 90, NULL, '성견 3개월마다 (접종 규칙 표 1장)'
  FROM vaccine_type WHERE code = 'DEWORM';
