-- V3__schedule_opened_by_visit.sql: 방문으로 연 주기에 그 방문을 남긴다 (#144, PR #202 리뷰, DB 설계 4-10)
-- 같은 방문으로 '방문 기록됨'을 다시 처리하면 아무것도 바꾸지 않아야 한다. 열린 주기가 없을 때 들어온 방문은 완료(done_visit_id)를
-- 남기지 않으므로, 그 방문이 새로 연 주기에 opened_by_visit_id를 남겨 다시 처리한 것을 알아본다.
-- 머지된 마이그레이션은 고치지 않는다. 값을 바꾸거나 더하면 번호를 올린 새 파일로 (CLAUDE.md 5번).

ALTER TABLE schedule
  ADD COLUMN opened_by_visit_id BIGINT NULL COMMENT '이 주기를 연 방문 (방문 뒤 다음 주기를 열 때만, 등록 · 다시 계산으로 연 주기는 NULL)' AFTER done_visit_id,
  ADD KEY idx_schedule_opened_by_visit (opened_by_visit_id),
  ADD CONSTRAINT fk_schedule_opened_by_visit FOREIGN KEY (opened_by_visit_id) REFERENCES visit (visit_id) ON DELETE RESTRICT;
