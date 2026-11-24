# frontend

Next.js (App Router) + TypeScript. 아직 코드가 없고, 스캐폴딩 이슈(#01 공통 기반)에서 아래 구조로 만든다. 구조를 바꾸려면 이 파일을 같은 PR에서 고친다.

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
│  │  │  └─ admin/                 백오피스 (관리자만)
│  │  │     ├─ staff/              B-01 직원 관리
│  │  │     ├─ rules/              B-02 주기 규칙
│  │  │     ├─ settings/           B-03 기준값 · 선택지 · 문자 양식
│  │  │     └─ history/            B-05 규칙 변경 이력
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

- 컴포넌트 이름은 피그마 컴포넌트 이름과 같게 쓴다 (`TargetRow`, `StatCard`, `TimelineItem`).
- 색 · 간격은 피그마 변수 이름을 CSS 변수로 옮겨 쓴다 (`--navy-900`, `--peri-500`, `--spacing-16`). hex를 직접 쓰지 않는다.
- 화면 문구는 정책 정의서 4장 문구를 그대로 쓴다.
- `components/ui`와 `components/layout`은 공용 파일이라 추가만 하고, 기존 컴포넌트를 바꾸려면 PR에 이유를 적는다.
