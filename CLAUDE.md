# CLAUDE.md

동물병원 재방문 관리 CRM "돌아올개" (팀 다다익선). 병원 직원(데스크 · 미용사 · 원장)이 반려동물별 접종 · 미용 예정일을 목록으로 챙기고, 보호자 연락 결과를 기록해 재방문으로 이어졌는지 숫자로 보는 웹 서비스다. 보호자 화면은 없다 (v2).

- 프론트엔드: TypeScript + Next.js (`frontend/`), 백엔드: Spring Boot + MySQL 8.0 (`backend/`)
- 일정: 스프린트 1 마감 11/27, 배포 12/4, 시연 12/11
- 사람용 규칙: [CONTRIBUTING.md](CONTRIBUTING.md). 이 파일은 그 규칙을 Claude가 지키도록 요약한 것이다.

## 먼저 읽을 곳

| 알고 싶은 것 | 파일 |
| --- | --- |
| 기능 목록 · 우선순위 · 완료 조건 | `docs/planning/요구사항 명세서(*).md` |
| 계산식 · 조건 · 화면 문구 | `docs/planning/정책 정의서(*).md` |
| 화면 흐름 · 화면 ID | `docs/planning/업무 분석_사용자(*).md`, `업무 분석_백오피스(*).md` |
| 테이블 · 컬럼 | `docs/design/DB 설계(*).md`, `docs/design/DDL(*).sql` |
| 폴더 구조 | `README.md`, `frontend/README.md`, `backend/README.md` |

파일 이름 끝의 날짜는 바뀔 수 있으니 `*`로 찾는다. 같은 문서가 여러 날짜로 있으면 가장 최근 날짜를 쓴다.

## ID

- 요구사항 `FR-업무-번호`, 비기능 `NFR-종류-번호`, 정책 `POL-영역-번호`, 직원 화면 `S-01`~`S-11`, 백오피스 화면 `B-01`~`B-05`.
- 업무 코드와 패키지 · 폴더가 1:1이다: AUTH→auth, CUS→customer, SRCH→search, VIS→visit, CYC→cycle, TGT→target, CNT→contact, DASH→dashboard, RPT→report, ADM→admin, COM→common, DATA→(seed 데이터).
- 이슈, 커밋, PR, 코드 주석에서 이 ID를 그대로 쓴다. 새 ID를 만들지 않는다.

## 꼭 지킬 것

1. **기획에 없는 기능을 만들지 않는다.** 필요해 보이면 멈추고 사용자에게 말한 뒤 `논의 필요` 이슈로 제안한다.
2. **팀이 결정판에서 정한 값은 고치지 않는다.** 문서끼리 어긋나 보여도 "정리"하지 말고 어긋난 곳을 보고만 한다.
3. **숫자 · 문구는 정책 정의서 그대로.** 누락 14일, 보류 30일, 전화 안 받음 다음 연락 1일 뒤, 화면 문구(정책 4장)를 바꾸거나 지어내지 않는다. 정책 숫자는 코드에 박지 않고 설정 테이블에서 읽는다.
4. **이슈 범위 밖 파일은 건드리지 않는다.** 공용 파일(`components/ui`, `components/layout`, `backend/.../common`, 설정 파일)은 추가만 하고, 바꿔야 하면 이유를 PR에 적는다.
5. **DB 변경은 새 마이그레이션 파일로만.** 머지된 마이그레이션은 고치지 않는다. 테이블 · 컬럼 이름은 DB 설계 문서와 같게.
6. **권한은 서버에서도 검사한다** (NFR-SEC-002). 화면 숨김만으로 끝내지 않는다.
7. **시간은 한국 시간** (Asia/Seoul). '오늘', '이번 주(월~일)' 판단도 한국 시간.
8. **비밀값 · `.env` · 실제 보호자 개인정보를 커밋하지 않는다.** 예시 데이터는 가짜 이름과 `010-0000-0000` 형식.

## 깃

- `main`에 직접 커밋 · push하지 않는다. 사용자가 시킬 때만 커밋 · push · PR을 한다.
- 브랜치: `feat/이슈번호-영문-요약`, `fix/…`, `docs/…`, `chore/…`, `refactor/…`, `test/…`
- 커밋: `종류: 한국어 요약 (#이슈번호)` — 종류는 feat · fix · docs · design · refactor · test · chore
- PR: 제목은 커밋 형식, 본문은 `.github/pull_request_template.md`를 채우고 `Closes #번호`를 넣는다.
- 한글 파일 이름이 깨지면 `git config core.quotepath false`.

## 문서를 쓸 때

- 파일 이름 `문서 이름(YYYY-MM-DD).md`. 기획은 `docs/planning/`, 설계는 `docs/design/`, 이슈 초안은 `docs/issues/`.
- 문서 머리 표의 '수정' 칸에 날짜와 고친 내용을 한 줄 더한다.
- 문장은 짧은 한국어 평서문. 기획 문서는 담당자가 고치고, 다른 사람이 고친 PR에는 문서 담당자를 리뷰어로 넣는다.

## 스킬

- `crm-issue-splitter`: 기획 문서를 이슈 단위로 쪼개 `docs/issues/`에 초안을 만든다.
- `crm-issue-coder`: 이슈 번호 하나를 받아 브랜치 → 구현 → 테스트 → PR까지 한다.

## 명령어

코드 스캐폴딩(#01 공통 기반) 뒤에 채운다. (예: `cd frontend && npm run dev`, `cd backend && ./gradlew test`)
