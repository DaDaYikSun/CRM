# backend

Spring Boot + MySQL 8.0. 아직 코드가 없고, 스캐폴딩 이슈(#01 공통 기반)에서 아래 구조로 만든다. 패키지는 요구사항 명세서의 업무 코드와 1:1로 맞춘다. 구조를 바꾸려면 이 파일을 같은 PR에서 고친다.

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
│  ├─ admin/         ADM  직원 · 규칙 · 기준값 · 변경 이력
│  └─ common/        COM  공통 응답 · 예외 · 시간(Asia/Seoul) · 감사 컬럼
│     (업무 패키지 안은 controller / service / repository / domain / dto)
├─ src/main/resources/
│  ├─ application.yml              공통 설정 (비밀값 없음)
│  ├─ application-local.yml        각자 PC용, 커밋 금지 (.gitignore)
│  └─ db/migration/                V1__init.sql 부터 (docs/design/DDL 기준)
├─ src/test/java/...               업무 패키지와 같은 구조
└─ .env.example
```

## 규칙

- 테이블 · 컬럼 이름은 [DB 설계](../docs/design/)의 이름을 그대로 쓴다.
- DB 변경은 `db/migration/`에 번호를 올린 새 파일로만 한다. 머지된 파일은 고치지 않는다.
- 권한 검사는 화면을 숨기는 것과 별개로 서버에서 한다 (NFR-SEC-002).
- 날짜 판단('오늘', '이번 주 월~일')은 한국 시간 기준이다 (DB 설계 1장).
- 정책 숫자(누락 14일, 보류 30일 등)는 코드에 박지 않고 설정 테이블에서 읽는다.
- `common/`은 공용 패키지라 추가만 하고, 기존 코드를 바꾸려면 PR에 이유를 적는다.
