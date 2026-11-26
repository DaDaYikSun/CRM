# frontend

Next.js 15 (App Router) + TypeScript. 구조를 바꾸려면 이 파일을 같은 PR에서 고친다.

## 실행

```bash
cp .env.example .env.local   # API_BASE_URL = Spring 서버 주소 (커밋 금지)
npm install
npm run dev                  # http://localhost:3000, /api/*는 Spring으로 넘어간다 (next.config.ts rewrites)
npm run build
npm run lint
npm test                     # vitest
```

```
frontend/
├─ src/
│  ├─ app/                         라우트 = 화면 ID
│  │  ├─ login/                    S-01 로그인 · 새 비밀번호
│  │  ├─ (main)/                   로그인 뒤 공통 레이아웃 (사이드바 + 상단 바)
│  │  │  ├─ dashboard/             S-02 대시보드
│  │  │  ├─ targets/               S-03 연락 대상 + S-04 연락 패널
│  │  │  ├─ search/                S-05 통합 검색
│  │  │  ├─ guardians/[id]/        S-06 보호자 상세
│  │  │  ├─ pets/[id]/             S-07 아이 상세 (+ S-09 방문 기록 모달)
│  │  │  ├─ register/              S-08 보호자·아이 등록
│  │  │  ├─ grooming/              S-11 미용 탭 (미용사는 여기만)
│  │  │  └─ admin/                 백오피스 (관리자만, 탭 6개 = 팀 결정 T8)
│  │  │     ├─ staff/              B-01 직원 관리
│  │  │     ├─ rules/              B-02 주기 규칙
│  │  │     ├─ settings/           B-03 기준값 · 선택지
│  │  │     ├─ message-templates/  B-04 문자 문구 · 병원 정보
│  │  │     ├─ report-template/    B-05 리포트 양식
│  │  │     └─ history/            B-06 변경 이력 (설정 변경 이력, FR-ADM-006)
│  │  └─ report/[petId]/           S-10 일정 요약 리포트 (새 탭, A4)
│  ├─ components/
│  │  ├─ ui/                       피그마 02 Components와 같은 이름 (Button, Badge, CautionTag, ResultButton …)
│  │  └─ layout/                   Sidebar, TopBar
│  ├─ features/                    업무 코드별 화면 로직 (contact/, target/, cycle/ …)
│  ├─ lib/
│  │  ├─ api/                      백엔드 호출 함수 (업무별 파일 하나)
│  │  └─ utils/                    날짜(한국 시간), 전화번호 정규화
│  ├─ types/                       API 응답 타입
│  └─ styles/                      피그마 Color · Size 변수 → CSS 변수
├─ public/
└─ .env.example                    실제 값은 .env.local (커밋 금지)
```

## 규칙

- 백엔드 호출은 `lib/api/client.ts`의 `api.get` · `api.post` · `api.put` · `api.patch`만 쓴다. 공통 응답 풀기, 조회 실패 한 번 자동 재시도(저장은 재시도 없음), CSRF 헤더, 저장 두 번 막기 키를 대신 해 준다 (POL-ERR-001 · 002, K10 · K15, `backend/README.md`).
- 저장 폼은 `newIdempotencyKey()`로 폼 하나에 키 하나를 만들어 `{ idempotencyKey }`로 넘기고, 저장이 끝날 때까지 같은 키를 쓴다. 서버는 오류 응답(4xx · 5xx)을 기억하지 않으므로, 입력 오류를 고쳐 같은 키로 다시 저장해도 새로 처리된다 (`backend/README.md` '저장 두 번 막기'). 저장이 성공하면 다음 저장에는 `newIdempotencyKey()`로 새 키를 만든다. 같은 키를 다시 쓰면 서버가 첫 성공 응답을 그대로 돌려줘 두 번째 수정이 저장되지 않는다. 오류 뒤 다시 저장하거나 응답을 못 받은 같은 저장을 다시 보낼 때는 키를 그대로 쓴다. 저장 중에는 `Button`의 `loading`으로 다시 누르지 못하게 한다.
- 오류는 `ApiError`(`code`, `message`, `fields`)로 온다. `message`는 정책 4-2 문구라 그대로 보여 준다.
- 날짜 · 전화번호는 `lib/utils/date.ts`(한국 시간, 이번 주 월~일) · `phone.ts`(숫자만)를 쓴다.
- 컴포넌트 이름은 피그마 컴포넌트 이름과 같게 쓴다 (`TargetRow`, `StatCard`, `TimelineItem`).
- 색 · 간격은 피그마 변수 이름을 CSS 변수로 옮겨 쓴다 (`--navy-900`, `--peri-500`, `--spacing-16`). hex를 직접 쓰지 않는다.
- 화면 문구는 정책 정의서 4장 문구를 그대로 쓴다.
- `components/ui`와 `components/layout`은 공용 파일이라 추가만 하고, 기존 컴포넌트를 바꾸려면 PR에 이유를 적는다.
