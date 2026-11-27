-- V4__vaccine_rule_dhppl_rabies.sql: 확정된 접종 규칙 중 종합백신 2행과 광견병 첫 예정 나이 (#207, 접종 규칙 표 3장, DB 설계 4-7)
-- V2 4장에서 미확정이라 뺀 행을 채운다. 접종 규칙 표 2장 2 · 4번이 확정됐다 (2026-11-27 양다연, #194, PR #195).
-- 선택 접종 3종(코로나 장염 · 켄넬코프 · 인플루엔자) 6행은 #204(기록 있는 아이만 예정) 머지 뒤 다음 번호 파일로 넣는다 (#207 선행 이슈).
-- 머지된 V2는 고치지 않는다. 값을 바꾸거나 더하면 번호를 올린 새 파일로 (CLAUDE.md 5번).

-- 종합백신: 생후 6주부터 2주 간격 5회 (국내 6차 접종표 1~5차, 2장 4번), 그 뒤 매년 (2장 2번)
INSERT INTO vaccine_rule (vaccine_type_id, phase, age_from_days, age_to_days, interval_days, dose_count, note)
SELECT vaccine_type_id, 'PRIMARY', 42, 365, 14, 5,
       '생후 6주부터 2주(14일) 간격 5회. 국내 6차 접종표 1~5차 (접종 규칙 표 1-1 · 2장 4번)'
  FROM vaccine_type WHERE code = 'DHPPL';
INSERT INTO vaccine_rule (vaccine_type_id, phase, age_from_days, age_to_days, interval_days, dose_count, note)
SELECT vaccine_type_id, 'BOOSTER', 365, NULL, 365, NULL, '매년 1회 (접종 규칙 표 2장 2번, 국내 관행)'
  FROM vaccine_type WHERE code = 'DHPPL';

-- 광견병 첫 예정: 생후 90일 → 112일(16주, 6차 접종표 6차). 90일이면 6차 방문(112일)이 누락 기준(예정일 + 14일 = 104일)을 넘는다
UPDATE vaccine_rule r JOIN vaccine_type t ON t.vaccine_type_id = r.vaccine_type_id
   SET r.age_from_days = 112,
       r.note = '생후 16주(112일)에 예정, 3개월 이상 1회. 3개월 미만에 맞혀도 이 행 1회를 채워 12개월 뒤 2차 (접종 규칙 표 1장 · 1-1)'
 WHERE t.code = 'RABIES' AND r.phase = 'PRIMARY';
