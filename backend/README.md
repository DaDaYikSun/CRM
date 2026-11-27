# backend

Spring Boot 3.4 (Java 21) + MySQL 8.0. 패키지는 요구사항 명세서의 업무 코드와 1:1로 맞춘다. 구조를 바꾸려면 이 파일을 같은 PR에서 고친다.

```
backend/
├─ src/main/java/com/dadayiksun/crm/
│  ├─ auth/          AUTH 로그인 · 역할 · 권한 검사
│  ├─ customer/      CUS  보호자 · 반려동물 · 주의 태그 · 무지개다리
│  ├─ search/        SRCH 통합 검색
│  ├─ visit/         VIS  방문 기록 (접종 · 미용)
│  ├─ cycle/         CYC  예정일 계산 (접종 규칙 · 미용 개인 주기)
│  ├─ target/        TGT  연락 대상 목록 (오늘 · 이번 주 · 누락)
│  ├─ contact/       CNT  연락 기록 · 모의 문자
│  ├─ dashboard/     DASH 지표 · 기간 계산
│  ├─ report/        RPT  일정 요약 리포트
│  ├─ admin/         ADM  백오피스 화면 · Controller (저장 규칙은 값의 주인 서비스, API 설계 4-2)
│  ├─ view/          조립 자리 [제안, #142]: 여러 도메인을 합치는 화면 응답 (아래 '조립 자리')
│  └─ common/        COM  공통 응답 · 예외 · 시간(Asia/Seoul) · 감사 컬럼 (아래 'common에 두는 것')
│     (업무 패키지 안은 controller / service / repository / domain / dto)
├─ src/main/resources/
│  ├─ application.yml              공통 설정 (비밀값 없음)
│  ├─ application-local.yml        각자 PC용, 커밋 금지 (.gitignore)
│  └─ db/migration/                V1__init.sql(= docs/design/DDL), V2__seed_base.sql(기본 데이터)
├─ src/test/java/...               업무 패키지와 같은 구조
└─ .env.example
```

## 실행

```bash
cp .env.example .env          # DB 접속 · 시드 계정 비밀번호를 채운다 (커밋 금지)
./gradlew bootRun             # 빈 DB면 Flyway가 V1 → V2를 넣고, 시드 계정을 만든다
./gradlew test
```

- MySQL 8.0, 스키마 `vet_crm`을 utf8mb4 / utf8mb4_0900_ai_ci로 먼저 만든다. 테이블은 Flyway가 만든다.
- 시드 계정: `admin`(관리자) · `desk`(데스크) · `groomer`(미용사). 비밀번호는 `SEED_*_PASSWORD` 환경 변수에서 읽어 BCrypt로 저장한다. 비우면 그 계정은 만들지 않는다.

## 규칙

- 테이블 · 컬럼 이름은 [DB 설계](../docs/design/)의 이름을 그대로 쓴다.
- DB 변경은 `db/migration/`에 번호를 올린 새 파일로만 한다. 머지된 파일은 고치지 않는다.
- 권한 검사는 화면을 숨기는 것과 별개로 서버에서 한다 (NFR-SEC-002).
- 날짜 판단('오늘', '이번 주 월~일')은 `KstClock`으로 한다. DB `CURDATE()`는 쓰지 않는다 (DB 설계 1장).
- 정책 숫자(누락 14일, 보류 30일 등)는 코드에 박지 않고 `SettingService`로 읽는다.
- `common/`은 공용 패키지라 추가만 하고, 기존 코드를 바꾸려면 PR에 이유를 적는다.

## common에 두는 것 (#142, K16 ③ · K22)

도메인 규칙(누락 · 전환 · 대상 판단 등)은 넣지 않는다.

| 하위 패키지 | 내용 | 쓰는 법 |
| --- | --- | --- |
| `response` | 공통 응답 C1: `ApiResponse`(`{ data }`), `PageResponse`(`{ items, page, size, total }`), `ErrorResponse`(`{ error: { code, message, fields? } }`) | Controller는 `ApiResponse.of(...)`를 돌려준다 |
| `error` | `ErrorCode`(API 설계 1-5 코드 · 정책 4-2 문구), `ApiException`, `GlobalExceptionHandler`(오류 응답을 만드는 한 곳) | 업무 오류는 `throw new ApiException(ErrorCode.X)`. 필수 칸 검사는 `@NotBlank(message = "칸 이름")`처럼 칸 이름만 적으면 "{항목}을(를) 입력해 주세요."가 된다 |
| `web` | `PageQuery`(20개 고정 · page 규칙, K16 ⑥), `IdempotencyFilter`(저장 두 번 막기, K15) | 목록 Controller는 `page`를 `String`으로 받아 `PageQuery.parse(page)` |
| `time` | `KstClock`: `today()`, `weekStart()`/`weekEnd()`(월~일), `nowWithOffset()`(+09:00) | '오늘'은 이 값을 쿼리 `:today`로 넘긴다 |
| `audit` | `AuditEntity`(created_by · created_at · updated_by · updated_at 자동 채움), `SessionKeys.STAFF_ID` | Entity가 `AuditEntity`를 상속. #143 로그인이 세션에 `SessionKeys.STAFF_ID`를 넣으면 '기록한 직원'이 채워진다 |
| `setting` | `SettingService.getInt/getString/getDate`, `SettingKey` (계약 C2) | `settingService.getInt(SettingKey.MISS_THRESHOLD_DAYS)` |
| `security` | `SecurityConfig`(CSRF, `PasswordEncoder` = BCrypt), 보안 필터 오류도 C1 형식 | 로그인 · 역할 검사는 #143이 더한다 |
| `seed` | 시드 계정 넣기 (POL-ACC-009) | 환경 변수 `SEED_*_PASSWORD` |
| (초안 25) | `RuleChangeLogWriter` (K22) | 초안 25가 `common`에 더한다 |

## CSRF 주고받는 방법 (K10, API 설계 1-6)

1. 화면이 첫 저장 요청 전에 `GET /api/csrf`를 부른다. 응답과 함께 쿠키 `XSRF-TOKEN`이 내려간다 (쿠키가 없으면 어느 응답에든 같이 내려간다).
2. 저장 요청(`POST` · `PUT` · `PATCH` · `DELETE`)마다 쿠키 `XSRF-TOKEN` 값을 헤더 `X-XSRF-TOKEN`에 싣는다.
3. 로그인하면 토큰이 바뀐다. 화면은 요청마다 쿠키를 새로 읽는다.
4. 토큰이 없거나 틀리면 `403 FORBIDDEN` (C1 형식).
5. 로그인 요청(`POST /api/auth/login`)도 같은 방법이다.

`frontend/src/lib/api/client.ts`가 1~3을 대신 한다. 화면 코드는 신경 쓰지 않아도 된다.

## 저장 두 번 막기 (K15, POL-ERR-002, API 설계 1-5)

- 화면은 저장 요청마다 새 키를 만들어 헤더 `Idempotency-Key`에 싣는다 (`client.ts`가 한다). 버튼 잠금만으로 끝내지 않는다.
- 서버(`IdempotencyFilter`)는 같은 세션 · 같은 메서드 · 주소 · 같은 키의 요청을 한 번만 처리하고, 두 번째 요청에는 처음 응답을 그대로 준다 (처음 요청이 처리 중이면 기다렸다가 같은 응답). 키는 10분 동안 기억한다.
- 오류 응답(4xx · 5xx)은 기억하지 않는다. 입력 오류(4xx)를 고쳐 같은 키로 다시 보내면 새로 처리하고, 서버 오류(5xx) 뒤에는 다시 저장할 수 있다. 그래서 저장 API는 쓰기를 커밋한 뒤에는 5xx로 답하지 않는다. 커밋 뒤 실패(문자 발송 등)는 성공 응답 안에서 알린다 (API 설계 1-5 '보장 범위', #189).
- 처음 요청이 처리 중이면 두 번째 요청은 기다린다. 기다려도 끝나지 않으면 저장하지 않고 `500 INTERNAL_ERROR`("저장하지 못했습니다. 다시 시도해 주세요.")로 답한다 [제안]. 같은 키로 다시 보내면 처음 응답을 받는다.
- 방문(`POST /api/pets/{id}/visits`) · 연락(`POST /api/contacts`, `POST /api/contacts/sms`) · 등록(`POST /api/guardians`, `POST /api/guardians/{id}/pets`)은 이 장치에 기댄다 (#145 · #148 · #153 · #174).
- 서버가 한 대라 메모리에 기억한다. 서버를 다시 켜거나 10분이 지나면 키가 사라져, 그 뒤 같은 키로 다시 보내면 새로 처리된다 (MVP 한계, #189). 서버를 늘리거나 이 한계를 줄여야 하면 저장소를 DB · Redis로 바꾼다.

## 조립 자리 [제안, #142] (API 설계 4-1)

여러 도메인을 합치는 화면 응답(아이 상세, 보호자 상세, 방문 저장 출력, 타임라인)은 `view/` 패키지에서 합친다. `view`는 모든 업무 패키지보다 위층이라 각 도메인의 Reader(인터페이스)를 읽을 수 있다. 쓰기는 하지 않고, 업무 패키지는 `view`를 import하지 않는다. 미용 목록 · 연락 패널처럼 한 도메인이 아래 도메인을 읽는 것은 그 도메인 안에서 한다.

## 시간대

- JVM 기본 시간대 Asia/Seoul (`CrmApplication`), JSON 시간은 `+09:00` (`spring.jackson.time-zone`).
- DB 연결마다 `SET time_zone = '+09:00'` (`spring.datasource.hikari.connection-init-sql`). `DEFAULT CURRENT_TIMESTAMP` 칸이 한국 시간으로 들어간다 (DB 설계 1장).
