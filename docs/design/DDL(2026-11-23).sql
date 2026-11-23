-- =====================================================================
-- 동물병원 재방문 관리 CRM - DDL
-- 기준: DB 설계(2026-11-23).md 초안 v1, MySQL 8.0 (8.0.16 이상: CHECK 제약 동작)
-- 실행 순서: 이 파일 전체를 위에서 아래로 실행
-- 주의: vaccine_rule 구조는 도메인 전문가 접종 규칙 표(11/27)를 받은 뒤 다시 맞춘다
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
) ENGINE=InnoDB COMMENT='기준값 (누락 기준, 연락 보류 기간 등)';

-- ---------------------------------------------------------------------
-- 2. 주기 계산 - 선택지·규칙
-- ---------------------------------------------------------------------

CREATE TABLE coat_type (
  coat_type_id         BIGINT       NOT NULL AUTO_INCREMENT,
  code                 VARCHAR(20)  NOT NULL COMMENT 'SHORT / MEDIUM / LONG_CURLY',
  name                 VARCHAR(20)  NOT NULL COMMENT '단모 / 중모 / 장모·곱슬',
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
) ENGINE=InnoDB COMMENT='접종 규칙 (구조 잠정, 11/27 확정)';

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
  phone_checked_at   DATE         NOT NULL DEFAULT (CURRENT_DATE) COMMENT '번호 마지막 확인일, 1년 지나면 배너 [POL-REG-008]',
  privacy_agreed     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '개인정보 수집·이용 동의 [POL-REG-010]',
  privacy_agreed_at  DATETIME     NULL,
  sms_agreed         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '안내 문자 수신 동의, 0이면 문자 버튼 끔',
  sms_agreed_at      DATETIME     NULL,
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
  CONSTRAINT fk_guardian_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT,
  CONSTRAINT fk_guardian_updated_by FOREIGN KEY (updated_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='보호자';

CREATE TABLE guardian_tag (
  guardian_id  BIGINT       NOT NULL,
  tag          ENUM('CONTACT_TIME','NO_CONTACT','SMS_ONLY','SENSITIVE') NOT NULL COMMENT '연락 선호 시간/연락 원치 않음/문자만/예민함',
  note         VARCHAR(50)  NULL COMMENT '태그 보충 (예: 오후 2시 이후)',
  created_by   BIGINT       NOT NULL,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (guardian_id, tag),
  KEY idx_guardian_tag_tag (tag),
  CONSTRAINT fk_guardian_tag_guardian   FOREIGN KEY (guardian_id) REFERENCES guardian (guardian_id) ON DELETE RESTRICT,
  CONSTRAINT fk_guardian_tag_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='주의 태그 (떼면 행 삭제)';

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

CREATE TABLE pet_status_history (
  pet_status_history_id  BIGINT    NOT NULL AUTO_INCREMENT,
  pet_id                 BIGINT    NOT NULL,
  from_status            ENUM('ACTIVE','RAINBOW') NOT NULL,
  to_status              ENUM('ACTIVE','RAINBOW') NOT NULL COMMENT 'RAINBOW -> ACTIVE = 되돌리기 (관리자만)',
  created_by             BIGINT    NOT NULL,
  created_at             DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (pet_status_history_id),
  KEY idx_pet_status_history_pet (pet_id, created_at),
  CONSTRAINT ck_pet_status_history_change CHECK (from_status <> to_status),
  CONSTRAINT fk_pet_status_history_pet        FOREIGN KEY (pet_id) REFERENCES pet (pet_id) ON DELETE RESTRICT,
  CONSTRAINT fk_pet_status_history_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='반려동물 상태 이력 (추가만)';

-- ---------------------------------------------------------------------
-- 4. 방문 기록
-- ---------------------------------------------------------------------

CREATE TABLE visit (
  visit_id          BIGINT        NOT NULL AUTO_INCREMENT,
  pet_id            BIGINT        NOT NULL,
  service_type      ENUM('VACCINE','GROOMING') NOT NULL COMMENT '접종 / 미용',
  vaccine_type_id   BIGINT        NULL COMMENT '접종이면 필수, 미용이면 NULL',
  visit_date        DATE          NOT NULL DEFAULT (CURRENT_DATE) COMMENT '방문일',
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
  missed_at            DATE      NULL COMMENT '누락 판정일, 방문해도 지우지 않음 [POL-MISS-001]',
  done_visit_id        BIGINT    NULL COMMENT '이 주기를 완료시킨 방문',
  done_date            DATE      NULL COMMENT '완료 방문일',
  gap_days             INT       NULL COMMENT 'done_date - due_date',
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
  KEY idx_schedule_pet (pet_id, service_type, status),
  KEY idx_schedule_missed (missed_at),
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
  sms_template        ENUM('VACCINE','GROOMING') NULL COMMENT '사용한 기본 문구',
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
  CONSTRAINT ck_contact_sms       CHECK (is_mock_sms = 0 OR method = 'SMS'),
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

CREATE TABLE record_edit_log (
  record_edit_log_id  BIGINT    NOT NULL AUTO_INCREMENT,
  target_type         ENUM('VISIT','CONTACT_LOG') NOT NULL COMMENT '고친 기록 종류',
  target_id           BIGINT    NOT NULL COMMENT '고친 기록 ID',
  before_json         JSON      NOT NULL COMMENT '고치기 전 값',
  after_json          JSON      NOT NULL COMMENT '고친 뒤 값',
  created_by          BIGINT    NOT NULL COMMENT '고친 직원',
  created_at          DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (record_edit_log_id),
  KEY idx_edit_target (target_type, target_id),
  CONSTRAINT fk_edit_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='기록 수정 이력 (추가만) [POL-DATA-006]';

CREATE TABLE rule_change_log (
  rule_change_log_id  BIGINT        NOT NULL AUTO_INCREMENT,
  target_table        ENUM('VACCINE_TYPE','VACCINE_RULE','COAT_TYPE','BREED','SETTING') NOT NULL COMMENT '바뀐 규칙 종류',
  target_key          VARCHAR(50)   NOT NULL COMMENT '바뀐 행 ID 또는 setting_key',
  field_name          VARCHAR(50)   NOT NULL COMMENT '바뀐 컬럼',
  old_value           VARCHAR(200)  NULL,
  new_value           VARCHAR(200)  NULL,
  created_by          BIGINT        NOT NULL COMMENT '바꾼 직원',
  created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (rule_change_log_id),
  KEY idx_rule_change_target (target_table, created_at),
  CONSTRAINT fk_rule_change_created_by FOREIGN KEY (created_by) REFERENCES staff (staff_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='규칙 변경 이력 (추가만) [FR-ADM-006]';

-- =====================================================================
-- 기본 데이터 (정책 정의서 6장에서 값이 정해진 것만)
-- 직원 계정·품종·접종 종류·접종 규칙은 시드 스크립트에서 넣는다
--   (계정 비밀번호는 환경 변수, 접종 값은 도메인 전문가 표 11/27)
-- =====================================================================

INSERT INTO coat_type (code, name, default_groom_weeks, sort_order) VALUES
  ('SHORT',      '단모',      10, 1),
  ('MEDIUM',     '중모',       7, 2),
  ('LONG_CURLY', '장모·곱슬',  5, 3);

INSERT INTO setting (setting_key, setting_value, value_type, description) VALUES
  ('MISS_THRESHOLD_DAYS', '14', 'INT',    '누락 기준: 예정일 + N일이 지나도 방문이 없으면 누락'),
  ('RAINBOW_HOLD_DAYS',   '30', 'INT',    '무지개다리 후 같은 집 다른 아이 연락 보류 기간(일)'),
  ('HOSPITAL_NAME',       '다다익선 동물병원', 'STRING', '문자 안내 문구의 병원 이름 (시연용)'),
  ('HOSPITAL_PHONE',      '0200000000', 'STRING', '문자 안내 문구의 병원 전화번호 (시연용, 실제로 쓰지 않는 번호)');
-- CRM_START_DATE(사용 전·후 비교 기준일)는 더미 데이터를 만들 때 넣는다 [FR-DATA-002]
