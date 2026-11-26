-- =====================================================================
-- 동물병원 재방문 관리 CRM - DDL
-- 기준: DB 설계(2026-11-23).md 초안 v3, MySQL 8.0 (8.0.16 이상: CHECK 제약 동작)
-- v2: 문자 문구·리포트 양식 설정(FR-ADM-008·009) 반영
-- v3: 테이블 합치기 17개 -> 14개
--     이력 3개(record_edit_log, rule_change_log, pet_status_history) -> change_log
--     guardian_tag -> guardian의 태그 칸
-- 2026-11-25: schedule.missed_at 뺌 (팀 결정 K3: 누락은 조회할 때 계산) (#57)
-- 2026-11-25: gap_days 주석을 DATEDIFF로 (#81)
-- 2026-11-25: schedule.missed_at · idx_schedule_missed 되살림 (11/25 회의 R1 · R9: 누락은 판정할 때 저장) (#89)
-- 2026-11-25: missed_at 주석에 기준 안 방문이면 완료 때 지움을 적음 (#109)
-- 2026-11-26: V1 전 정리 (#136). 리포트 양식 기본값을 T5로, DATE 기본값 (CURRENT_DATE) 뺌(앱이 한국 날짜를 넣음), 접종 규칙 확정 대기 표시 정리 (행 예시 · 미확정 값은 DB 설계 4-7)
-- 2026-11-26: 8. 세션 절 추가 - Spring Session JDBC 테이블 2개 (팀 결정 K19, #136)
-- 실행 순서: 이 파일 전체를 위에서 아래로 실행
-- =====================================================================

CREATE DATABASE IF NOT EXISTS vet_crm
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE vet_crm;

SET time_zone = '+09:00';

-- ---------------------------------------------------------------------
-- 1. 운영
-- ---------------------------------------------------------------------

CREATE TABLE staff (
  staff_id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '직원 ID',
  login_id              VARCHAR(30)   NOT NULL COMMENT '로그인 아이디',
  password_hash         VARCHAR(100)  NOT NULL COMMENT 'BCrypt 해시 (평문 저장 금지)',
  name                  VARCHAR(30)   NOT NULL COMMENT '이름',
  role                  ENUM('ADMIN','DESK','GROOMER') NOT NULL COMMENT '관리자(원장·데스크 실장)/데스크/미용사 [POL-ACC-001]',
  is_active             TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '0 = 비활성(퇴사), 로그인 불가 [POL-ACC-004]',
  deactivated_at        DATETIME      NULL COMMENT '비활성화한 시간',
  must_change_password  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '1 = 임시 비밀번호, 로그인 뒤 새 비밀번호 설정 필요 [POL-ACC-010]',
  last_login_at         DATETIME      NULL COMMENT '마지막 로그인',
  created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (staff_id),
  UNIQUE KEY uk_staff_login_id (login_id)
) ENGINE=InnoDB COMMENT='직원';

CREATE TABLE setting (
  setting_key    VARCHAR(50)   NOT NULL COMMENT '설정 키',
  setting_value  VARCHAR(100)  NOT NULL COMMENT '값 (문자로 저장, 서버가 value_type대로 변환)',
  value_type     ENUM('INT','DATE','STRING') NOT NULL COMMENT '값 종류',
  description    VARCHAR(200)  NULL COMMENT '화면에 보일 설명',
  updated_by     BIGINT        NULL,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_key),
  CONSTRAINT fk_setting_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='기준값·병원 정보 (누락 기준, 연락 보류 기간, 병원 이름·전화번호 등)';

CREATE TABLE message_template (
  template_code  ENUM('VACCINE','GROOMING') NOT NULL COMMENT '접종 안내 / 미용 안내 [정책 4-3]',
  body           VARCHAR(1000) NOT NULL COMMENT '지금 쓰는 문구 (관리자가 수정) [FR-ADM-008]',
  default_body   VARCHAR(1000) NOT NULL COMMENT '[기본 문구로 되돌리기]용 원래 문구',
  updated_by     BIGINT        NULL,
  updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (template_code),
  CONSTRAINT ck_message_template_body CHECK (body LIKE '%{아이 이름}%' AND body LIKE '%{예정일}%'),
  CONSTRAINT fk_message_template_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='문자 안내 문구 [POL-CNT-005]';

CREATE TABLE report_template (
  report_template_id  TINYINT       NOT NULL DEFAULT 1 COMMENT '행 1개로 고정',
  title               VARCHAR(50)   NOT NULL DEFAULT '{아이 이름}의 일정 요약' COMMENT '리포트 제목 [T5, POL-RPT-001]',
  show_pet_info       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '아이 정보',
  show_next_due       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '다음 예정일',
  show_history        TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '지난 접종·미용',
  show_guidance       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '병원에서 드린 안내 (연락 기록 요약)',
  history_months      SMALLINT      NULL DEFAULT 12 COMMENT '지난 이력 기간(개월), NULL = 전체',
  footer_text         VARCHAR(300)  NULL DEFAULT '진료 내용은 이 요약에 담지 않습니다. 예정일은 아이의 기록으로 계산한 날짜입니다.' COMMENT '아래 안내 문구 [T5, POL-RPT-001]',
  updated_by          BIGINT        NULL,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (report_template_id),
  CONSTRAINT ck_report_template_single  CHECK (report_template_id = 1),
  CONSTRAINT ck_report_template_items   CHECK (show_pet_info + show_next_due + show_history + show_guidance >= 1),
  CONSTRAINT ck_report_template_months  CHECK (history_months IS NULL OR history_months > 0),
  CONSTRAINT fk_report_template_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='리포트 양식 (진료 내용 칸은 일부러 없음) [POL-RPT-001·002]';

-- ---------------------------------------------------------------------
-- 2. 주기 계산 - 선택지·규칙
-- ---------------------------------------------------------------------

CREATE TABLE coat_type (
  coat_type_id         BIGINT       NOT NULL AUTO_INCREMENT,
  code                 VARCHAR(20)  NOT NULL COMMENT 'SHORT / MEDIUM / LONG / CURLY',
  name                 VARCHAR(20)  NOT NULL COMMENT '단모 / 중모 / 장모 / 곱슬',
  default_groom_weeks  TINYINT      NOT NULL COMMENT '미용 기본 간격(주) [POL-CYC-002]',
  sort_order           INT          NOT NULL DEFAULT 0,
  is_active            TINYINT(1)   NOT NULL DEFAULT 1,
  created_by           BIGINT       NULL,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by           BIGINT       NULL,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (coat_type_id),
  UNIQUE KEY uk_coat_type_code (code),
  CONSTRAINT ck_coat_type_weeks CHECK (default_groom_weeks > 0),
  CONSTRAINT fk_coat_type_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_coat_type_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='털 유형·미용 기본 간격';

CREATE TABLE breed (
  breed_id              BIGINT       NOT NULL AUTO_INCREMENT,
  species               ENUM('DOG')  NOT NULL DEFAULT 'DOG' COMMENT '동물 종류 [POL-REG-003]',
  name                  VARCHAR(50)  NOT NULL COMMENT '품종 이름',
  default_coat_type_id  BIGINT       NULL COMMENT '품종을 고르면 미리 골라 둘 털 유형',
  sort_order            INT          NOT NULL DEFAULT 0,
  is_active             TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '0 = 선택지에서 숨김',
  created_by            BIGINT       NULL,
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by            BIGINT       NULL,
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (breed_id),
  UNIQUE KEY uk_breed_species_name (species, name),
  CONSTRAINT fk_breed_coat_type  FOREIGN KEY (default_coat_type_id) REFERENCES coat_type (coat_type_id) ON DELETE RESTRICT,
  CONSTRAINT fk_breed_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_breed_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='품종 선택지';

CREATE TABLE vaccine_type (
  vaccine_type_id  BIGINT       NOT NULL AUTO_INCREMENT,
  code             VARCHAR(30)  NOT NULL COMMENT 'DHPPL, RABIES, HEARTWORM 등',
  name             VARCHAR(50)  NOT NULL COMMENT '화면 이름',
  is_major         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 = 주요 접종, 접종 누락률에 포함 [POL-MISS-004]',
  sort_order       INT          NOT NULL DEFAULT 0,
  is_active        TINYINT(1)   NOT NULL DEFAULT 1,
  created_by       BIGINT       NULL,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT       NULL,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (vaccine_type_id),
  UNIQUE KEY uk_vaccine_type_code (code),
  CONSTRAINT fk_vaccine_type_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_vaccine_type_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='접종 종류';

CREATE TABLE vaccine_rule (
  vaccine_rule_id  BIGINT        NOT NULL AUTO_INCREMENT,
  vaccine_type_id  BIGINT        NOT NULL,
  phase            ENUM('PRIMARY','BOOSTER','PREVENTIVE') NOT NULL COMMENT '기초접종 / 추가접종 / 예방약',
  age_from_days    INT           NOT NULL COMMENT '구간 시작 나이(일), 기록이 없으면 이 나이가 첫 예정일',
  age_to_days      INT           NULL COMMENT '구간 끝 나이(일, 미만), NULL = 끝 없음',
  interval_days    INT           NOT NULL COMMENT '마지막 접종일 + 이 값 = 다음 예정일',
  dose_count       TINYINT       NULL COMMENT '이 구간 접종 횟수, NULL = 계속 반복',
  note             VARCHAR(200)  NULL COMMENT '근거 자료 메모',
  is_active        TINYINT(1)    NOT NULL DEFAULT 1,
  created_by       BIGINT        NULL,
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by       BIGINT        NULL,
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (vaccine_rule_id),
  KEY idx_vaccine_rule_type (vaccine_type_id),
  CONSTRAINT ck_vaccine_rule_age      CHECK (age_from_days >= 0 AND (age_to_days IS NULL OR age_to_days > age_from_days)),
  CONSTRAINT ck_vaccine_rule_interval CHECK (interval_days > 0),
  CONSTRAINT fk_vaccine_rule_type       FOREIGN KEY (vaccine_type_id) REFERENCES vaccine_type (vaccine_type_id) ON DELETE RESTRICT,
  CONSTRAINT fk_vaccine_rule_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_vaccine_rule_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='접종 규칙 (나이 구간별 간격) [K9]';

-- ---------------------------------------------------------------------
-- 3. 고객 관리
-- ---------------------------------------------------------------------

CREATE TABLE guardian (
  guardian_id        BIGINT       NOT NULL AUTO_INCREMENT,
  name               VARCHAR(30)  NOT NULL COMMENT '보호자 이름',
  name_chosung       VARCHAR(30)  NOT NULL COMMENT '이름 초성, 서버가 채움 [FR-SRCH-003]',
  phone              VARCHAR(11)  NOT NULL COMMENT '숫자만. UNIQUE 아님 (같은 번호도 새로 등록 가능) [POL-REG-005]',
  phone_last4        CHAR(4)      AS (RIGHT(phone, 4)) STORED COMMENT '번호 뒤 4자리',
  sub_phone          VARCHAR(11)  NULL COMMENT '보조 연락처(가족)',
  preferred_contact  ENUM('PHONE','SMS') NULL COMMENT '선호 연락 방법',
  phone_status       ENUM('OK','WRONG') NOT NULL DEFAULT 'OK' COMMENT 'WRONG = 번호 오류 [FR-DASH-004]',
  phone_checked_at   DATE         NOT NULL COMMENT '번호 마지막 확인일, 1년 지나면 배너. 앱이 한국 날짜로 넣음 (DB CURRENT_DATE 안 씀) [POL-REG-008]',
  privacy_agreed     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '개인정보 수집·이용 동의 [POL-REG-010]',
  privacy_agreed_at  DATETIME     NULL,
  sms_agreed         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '안내 문자 수신 동의, 0이면 문자 버튼 끔',
  sms_agreed_at      DATETIME     NULL,
  tag_no_contact     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '주의 태그: 연락 원치 않음, 1이면 연락 대상에서 뺌 [POL-TGT-004]',
  tag_sms_only       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '주의 태그: 문자만',
  tag_sensitive      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '주의 태그: 예민함',
  tag_contact_time   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '주의 태그: 연락 선호 시간',
  contact_time_note  VARCHAR(50)  NULL COMMENT '연락 선호 시간 내용 (예: 오후 2시 이후)',
  created_by         BIGINT       NOT NULL,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by         BIGINT       NULL,
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (guardian_id),
  KEY idx_guardian_phone (phone),
  KEY idx_guardian_phone_last4 (phone_last4),
  KEY idx_guardian_name (name),
  KEY idx_guardian_chosung (name_chosung),
  KEY idx_guardian_phone_status (phone_status),
  CONSTRAINT ck_guardian_phone     CHECK (phone REGEXP '^[0-9]{9,11}$'),
  CONSTRAINT ck_guardian_sub_phone CHECK (sub_phone IS NULL OR sub_phone REGEXP '^[0-9]{9,11}$'),
  CONSTRAINT ck_guardian_contact_time CHECK (contact_time_note IS NULL OR tag_contact_time = 1),
  CONSTRAINT fk_guardian_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_guardian_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='보호자 (주의 태그 4가지 포함) [FR-CUS-005]';

CREATE TABLE pet (
  pet_id                  BIGINT         NOT NULL AUTO_INCREMENT,
  guardian_id             BIGINT         NOT NULL,
  name                    VARCHAR(30)    NOT NULL COMMENT '아이 이름',
  name_chosung            VARCHAR(30)    NOT NULL COMMENT '이름 초성',
  species                 ENUM('DOG')    NOT NULL DEFAULT 'DOG' COMMENT '동물 종류 [NFR-EXT-002]',
  breed_id                BIGINT         NOT NULL,
  coat_type_id            BIGINT         NOT NULL,
  birth_date              DATE           NOT NULL COMMENT '생년월일 (추정이면 오늘 - 대략 나이)',
  birth_date_estimated    TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '1 = 추정 생년월일 [POL-REG-004]',
  registration_no         VARCHAR(20)    NULL COMMENT '병원 등록번호',
  photo_url               VARCHAR(255)   NULL COMMENT '사진 경로 (거절하면 NULL)',
  has_pet_insurance       TINYINT(1)     NULL COMMENT '펫보험 가입 여부, NULL = 모름',
  weight_kg               DECIMAL(4,1)   NULL COMMENT '현재 체중',
  status                  ENUM('ACTIVE','RAINBOW') NOT NULL DEFAULT 'ACTIVE' COMMENT 'RAINBOW = 무지개다리 [POL-END-001]',
  rainbow_at              DATETIME       NULL COMMENT '무지개다리로 바꾼 시간',
  contact_hold_until      DATE           NULL COMMENT '이 날까지 연락 보류 [POL-TGT-003]',
  contact_hold_by_pet_id  BIGINT         NULL COMMENT '보류 원인이 된 아이 (되돌리기용) [POL-END-004]',
  groom_interval_days     SMALLINT       NULL COMMENT '개인 미용 주기 캐시',
  groom_interval_basis    ENUM('DEFAULT','MEDIAN') NULL COMMENT '기본 간격 / 중앙값',
  created_by              BIGINT         NOT NULL,
  created_at              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by              BIGINT         NULL,
  updated_at              DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (pet_id),
  KEY idx_pet_guardian (guardian_id),
  KEY idx_pet_name (name),
  KEY idx_pet_chosung (name_chosung),
  KEY idx_pet_status (status),
  KEY idx_pet_hold (contact_hold_until),
  CONSTRAINT ck_pet_weight  CHECK (weight_kg IS NULL OR weight_kg > 0),
  CONSTRAINT ck_pet_rainbow CHECK ((status = 'RAINBOW') = (rainbow_at IS NOT NULL)),
  CONSTRAINT fk_pet_guardian     FOREIGN KEY (guardian_id) REFERENCES guardian (guardian_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pet_breed        FOREIGN KEY (breed_id) REFERENCES breed (breed_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pet_coat_type    FOREIGN KEY (coat_type_id) REFERENCES coat_type (coat_type_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pet_hold_by_pet  FOREIGN KEY (contact_hold_by_pet_id) REFERENCES pet (pet_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pet_created_by   FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pet_updated_by   FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='반려동물';

-- ---------------------------------------------------------------------
-- 4. 방문 기록
-- ---------------------------------------------------------------------

CREATE TABLE visit (
  visit_id          BIGINT        NOT NULL AUTO_INCREMENT,
  pet_id            BIGINT        NOT NULL,
  service_type      ENUM('VACCINE','GROOMING') NOT NULL COMMENT '접종 / 미용',
  vaccine_type_id   BIGINT        NULL COMMENT '접종이면 필수, 미용이면 NULL',
  visit_date        DATE          NOT NULL COMMENT '방문일. 앱이 한국 날짜로 넣음 (DB CURRENT_DATE 안 씀)',
  memo              VARCHAR(200)  NULL COMMENT '메모 200자 [POL-DATA-010]',
  brought_by_phone  VARCHAR(11)   NULL COMMENT '데려온 가족 번호 (보호자 정보는 안 바꿈) [POL-REG-006]',
  is_edited         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '1 = 수정됨 [POL-DATA-007]',
  created_by        BIGINT        NOT NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by        BIGINT        NULL,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (visit_id),
  KEY idx_visit_pet (pet_id, service_type, visit_date),
  CONSTRAINT ck_visit_vaccine CHECK (
    (service_type = 'VACCINE'  AND vaccine_type_id IS NOT NULL) OR
    (service_type = 'GROOMING' AND vaccine_type_id IS NULL)
  ),
  CONSTRAINT ck_visit_brought_by CHECK (brought_by_phone IS NULL OR brought_by_phone REGEXP '^[0-9]{9,11}$'),
  CONSTRAINT fk_visit_pet          FOREIGN KEY (pet_id) REFERENCES pet (pet_id) ON DELETE RESTRICT,
  CONSTRAINT fk_visit_vaccine_type FOREIGN KEY (vaccine_type_id) REFERENCES vaccine_type (vaccine_type_id) ON DELETE RESTRICT,
  CONSTRAINT fk_visit_created_by   FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_visit_updated_by   FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='방문 기록 (접종 1종류 또는 미용 1번 = 1행)';

-- ---------------------------------------------------------------------
-- 5. 예정 일정
-- ---------------------------------------------------------------------

CREATE TABLE schedule (
  schedule_id          BIGINT    NOT NULL AUTO_INCREMENT,
  pet_id               BIGINT    NOT NULL,
  service_type         ENUM('VACCINE','GROOMING') NOT NULL,
  vaccine_type_id      BIGINT    NULL COMMENT '접종이면 필수 [Q1]',
  due_date             DATE      NOT NULL COMMENT '개인 예정일',
  status               ENUM('OPEN','DONE','VOID') NOT NULL DEFAULT 'OPEN' COMMENT '진행 중 / 완료 / 다시 계산으로 무효',
  miss_threshold_days  SMALLINT  NOT NULL COMMENT '만들 때 설정값 복사, 지난 판정 고정 [POL-CYC-005]',
  done_visit_id        BIGINT    NULL COMMENT '이 주기를 완료시킨 방문',
  done_date            DATE      NULL COMMENT '완료 방문일',
  gap_days             INT       NULL COMMENT 'DATEDIFF(done_date, due_date)',
  missed_at            DATE      NULL COMMENT '누락 판정일. 기준 밖 늦은 완료는 그대로, 방문일이 기준 안이면 완료 때 NULL [11/25 회의 R1, POL-MISS-001, #109]',
  contact_state        ENUM('NONE','NO_ANSWER','BOOKED','REFUSED','OTHER_HOSPITAL','WRONG_NUMBER','CLOSED')
                                 NOT NULL DEFAULT 'NONE' COMMENT '이 주기의 마지막 연락 결과 (대상 제외 판단) [POL-TGT-005~009]',
  no_answer_count      TINYINT   NOT NULL DEFAULT 0 COMMENT '전화 안 받음 연속 횟수 [POL-TGT-006]',
  next_contact_date    DATE      NULL COMMENT '다음 연락 예정일',
  active_key           VARCHAR(50) AS (
                         IF(status = 'OPEN',
                            CONCAT(pet_id, '-', service_type, '-', IFNULL(vaccine_type_id, 0)),
                            NULL)
                       ) STORED COMMENT '진행 중 주기 중복 방지용',
  created_at           DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (schedule_id),
  UNIQUE KEY uk_schedule_active (active_key),
  UNIQUE KEY uk_schedule_done_visit (done_visit_id),
  KEY idx_schedule_target (status, due_date),
  KEY idx_schedule_missed (missed_at),
  KEY idx_schedule_pet (pet_id, service_type, status),
  KEY idx_schedule_next_contact (next_contact_date),
  KEY idx_schedule_contact_state (contact_state),
  CONSTRAINT ck_schedule_vaccine CHECK (
    (service_type = 'VACCINE'  AND vaccine_type_id IS NOT NULL) OR
    (service_type = 'GROOMING' AND vaccine_type_id IS NULL)
  ),
  CONSTRAINT ck_schedule_done CHECK (
    (status = 'DONE') = (done_visit_id IS NOT NULL AND done_date IS NOT NULL)
  ),
  CONSTRAINT ck_schedule_threshold CHECK (miss_threshold_days > 0),
  CONSTRAINT fk_schedule_pet          FOREIGN KEY (pet_id) REFERENCES pet (pet_id) ON DELETE RESTRICT,
  CONSTRAINT fk_schedule_vaccine_type FOREIGN KEY (vaccine_type_id) REFERENCES vaccine_type (vaccine_type_id) ON DELETE RESTRICT,
  CONSTRAINT fk_schedule_done_visit   FOREIGN KEY (done_visit_id) REFERENCES visit (visit_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='예정 일정 (아이 x 서비스 한 주기 = 1행)';

-- ---------------------------------------------------------------------
-- 6. 연락 관리
-- ---------------------------------------------------------------------

CREATE TABLE contact_log (
  contact_log_id      BIGINT        NOT NULL AUTO_INCREMENT,
  contact_group_id    CHAR(36)      NOT NULL COMMENT '한 번에 저장한 묶음 (UUID)',
  guardian_id         BIGINT        NOT NULL,
  pet_id              BIGINT        NOT NULL,
  schedule_id         BIGINT        NOT NULL COMMENT '대상 예정 항목 [POL-CNT-002]',
  method              ENUM('PHONE','SMS') NOT NULL COMMENT '연락 방법',
  result              ENUM('BOOKED','NO_ANSWER','REFUSED','OTHER_HOSPITAL','WRONG_NUMBER') NULL
                                    COMMENT '결과 유형 5가지, 문자 모의 발송만 했으면 NULL',
  is_mock_sms         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '문자 모의 발송 [POL-CNT-005]',
  sms_template        ENUM('VACCINE','GROOMING') NULL COMMENT '어느 문구를 썼는지',
  sms_body            VARCHAR(1000) NULL COMMENT '실제로 보낸 문구 원문 (문구를 고쳐도 남음)',
  talked_with         ENUM('SELF','FAMILY') NULL COMMENT '통화한 사람',
  memo                VARCHAR(200)  NULL COMMENT '메모 200자 [POL-DATA-010]',
  next_contact_date   DATE          NULL COMMENT '입력한 다음 연락 예정일',
  contacted_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '연락한 시간',
  is_converted        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '1 = 재방문 전환 [POL-MISS-005~007]',
  converted_visit_id  BIGINT        NULL COMMENT '전환된 방문',
  is_edited           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '1 = 수정됨',
  created_by          BIGINT        NOT NULL COMMENT '기록한 직원',
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_by          BIGINT        NULL,
  updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (contact_log_id),
  KEY idx_contact_group (contact_group_id),
  KEY idx_contact_guardian (guardian_id, contacted_at),
  KEY idx_contact_schedule (schedule_id, contacted_at),
  KEY idx_contact_pet (pet_id, contacted_at),
  KEY idx_contact_stats (contacted_at, result, method),
  CONSTRAINT ck_contact_result    CHECK (method = 'SMS' OR result IS NOT NULL),
  CONSTRAINT ck_contact_sms       CHECK (is_mock_sms = 0 OR (method = 'SMS' AND sms_body IS NOT NULL)),
  CONSTRAINT ck_contact_converted CHECK (is_converted = (converted_visit_id IS NOT NULL)),
  CONSTRAINT fk_contact_guardian   FOREIGN KEY (guardian_id) REFERENCES guardian (guardian_id) ON DELETE RESTRICT,
  CONSTRAINT fk_contact_pet        FOREIGN KEY (pet_id) REFERENCES pet (pet_id) ON DELETE RESTRICT,
  CONSTRAINT fk_contact_schedule   FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id) ON DELETE RESTRICT,
  CONSTRAINT fk_contact_visit      FOREIGN KEY (converted_visit_id) REFERENCES visit (visit_id) ON DELETE RESTRICT,
  CONSTRAINT fk_contact_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_contact_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='연락 기록 (예정 항목마다 1행)';

-- ---------------------------------------------------------------------
-- 7. 이력
-- ---------------------------------------------------------------------

-- 기록 수정, 규칙·설정 변경, 반려동물 상태 변경(무지개다리), 주의 태그 변경을 한 테이블에 쌓는다
-- before_json / after_json에는 바뀐 칸만 넣는다. 예: {"status": "ACTIVE"} -> {"status": "RAINBOW"}
CREATE TABLE change_log (
  change_log_id  BIGINT       NOT NULL AUTO_INCREMENT,
  category       ENUM('RECORD_EDIT','RULE_CHANGE','STATUS_CHANGE','TAG_CHANGE') NOT NULL
                              COMMENT '기록 수정 [POL-DATA-006] / 규칙·설정 변경 [FR-ADM-006] / 아이 상태 변경 [POL-END-004] / 주의 태그 변경',
  target_type    ENUM('VISIT','CONTACT_LOG','PET','GUARDIAN',
                      'VACCINE_TYPE','VACCINE_RULE','COAT_TYPE','BREED',
                      'SETTING','MESSAGE_TEMPLATE','REPORT_TEMPLATE') NOT NULL COMMENT '바뀐 대상 테이블',
  target_id      VARCHAR(50)  NOT NULL COMMENT '바뀐 행의 ID, setting은 setting_key, 문구는 template_code',
  before_json    JSON         NULL COMMENT '바꾸기 전 값 (바뀐 칸만)',
  after_json     JSON         NOT NULL COMMENT '바꾼 뒤 값 (바뀐 칸만)',
  created_by     BIGINT       NOT NULL COMMENT '바꾼 직원',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (change_log_id),
  KEY idx_change_target (target_type, target_id, created_at),
  KEY idx_change_category (category, created_at),
  CONSTRAINT fk_change_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='변경 이력 (추가만, 수정·삭제 금지) [NFR-DATA-002]';

-- =====================================================================
-- 기본 데이터 (정책 정의서 6장에서 값이 정해진 것만)
-- 직원 계정·품종·접종 종류·접종 규칙은 시드 스크립트에서 넣는다
--   (계정 비밀번호는 환경 변수, 접종 값은 접종 규칙 표 · K9)
-- =====================================================================

INSERT INTO coat_type (code, name, default_groom_weeks, sort_order) VALUES
  ('SHORT',  '단모', 10, 1),
  ('MEDIUM', '중모',  7, 2),
  ('LONG',   '장모',  5, 3),
  ('CURLY',  '곱슬',  5, 4);

INSERT INTO setting (setting_key, setting_value, value_type, description) VALUES
  ('MISS_THRESHOLD_DAYS', '14', 'INT',    '누락 기준: 예정일 + N일이 지나도 방문이 없으면 누락'),
  ('RAINBOW_HOLD_DAYS',   '30', 'INT',    '무지개다리 후 같은 집 다른 아이 연락 보류 기간(일)'),
  ('HOSPITAL_NAME',       '다다익선 동물병원', 'STRING', '병원 이름: 문자와 리포트에 같이 씀, 문자 문구 탭에서 수정 [FR-ADM-008]'),
  ('HOSPITAL_PHONE',      '0200000000', 'STRING', '병원 전화번호(숫자만): 문자와 리포트에 같이 씀, 문자 문구 탭에서 수정 (시연용, 실제로 쓰지 않는 번호)');
-- CRM_START_DATE(사용 전·후 비교 기준일)는 더미 데이터를 만들 때 넣는다 [FR-DATA-002]

INSERT INTO message_template (template_code, body, default_body) VALUES
  ('VACCINE',
   '[{병원 이름}] {아이 이름}의 {접종 종류} 접종 예정일은 {예정일}입니다. 방문 전 {병원 전화번호}로 예약해 주세요.',
   '[{병원 이름}] {아이 이름}의 {접종 종류} 접종 예정일은 {예정일}입니다. 방문 전 {병원 전화번호}로 예약해 주세요.'),
  ('GROOMING',
   '[{병원 이름}] {아이 이름}의 다음 미용 예정일은 {예정일} 무렵입니다. 원하시는 날짜를 {병원 전화번호}로 알려 주세요.',
   '[{병원 이름}] {아이 이름}의 다음 미용 예정일은 {예정일} 무렵입니다. 원하시는 날짜를 {병원 전화번호}로 알려 주세요.');

-- 리포트 양식 1행 (제목 · 아래 안내 · 지난 이력 12개월은 컬럼 기본값 = 팀 결정 T5)
INSERT INTO report_template (report_template_id) VALUES (1);

-- ---------------------------------------------------------------------
-- 8. 세션 (Spring Session JDBC, K19)
-- Spring Session 공식 schema-mysql.sql 그대로 둔다.
-- 테이블 이름은 대문자 그대로 (Railway MySQL은 리눅스라 테이블 이름 대소문자를 구분, Spring Session 기본 이름과 같아야 함).
-- 이름 · FK는 1장 원칙(snake_case 단수형, ON DELETE RESTRICT)의 예외다. 만료 세션 정리 때 속성 행이 같이 지워져야 해서 CASCADE를 둔다.
-- 테이블은 Flyway(V1)가 만들고, 앱은 spring.session.jdbc.initialize-schema=never (#142).
-- ---------------------------------------------------------------------

CREATE TABLE SPRING_SESSION (
  PRIMARY_ID            CHAR(36)     NOT NULL,
  SESSION_ID            CHAR(36)     NOT NULL,
  CREATION_TIME         BIGINT       NOT NULL,
  LAST_ACCESS_TIME      BIGINT       NOT NULL,
  MAX_INACTIVE_INTERVAL INT          NOT NULL,
  EXPIRY_TIME           BIGINT       NOT NULL,
  PRINCIPAL_NAME        VARCHAR(100) NULL,
  CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
  ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES    BLOB         NOT NULL,
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
    REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
