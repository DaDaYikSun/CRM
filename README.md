# CRM

동물병원 재방문 관리 CRM 프로젝트 저장소입니다. (팀 다다익선 · 프로젝트 돌아올개)

- 기술: 프론트엔드 TypeScript + Next.js, 백엔드 Spring + MySQL 8.0
- 일정: 스프린트 1 마감 11/27 · 배포 12/4 · 시연 12/11
- 작업 규칙: [CONTRIBUTING.md](CONTRIBUTING.md) (이슈 · 브랜치 · 커밋 · PR)
- Claude Code 규칙: [CLAUDE.md](CLAUDE.md), [.claude/skills/](.claude/skills/)

## 폴더 구조

```
CRM/
├─ README.md               이 파일 (폴더 지도)
├─ CONTRIBUTING.md         깃 관리 규칙
├─ CLAUDE.md               Claude Code가 읽는 프로젝트 규칙
├─ .github/
│  ├─ ISSUE_TEMPLATE/      이슈 템플릿 (기능 · 버그 · 문서·설정)
│  └─ pull_request_template.md
├─ .claude/skills/         팀 공용 Claude 스킬 (이슈 쪼개기, 이슈 구현)
├─ docs/
│  ├─ planning/            기획 문서: 무엇을, 왜, 어떤 규칙으로
│  │  └─ images/
│  ├─ design/              설계 문서: 어떻게 (DB · DDL · API · 화면)
│  └─ issues/              이슈 초안 md와 작업분배표
├─ frontend/               Next.js 앱 (구조는 frontend/README.md)
└─ backend/                Spring 앱 (구조는 backend/README.md)
```

## 문서 위치

| 폴더 | 넣는 문서 | 지금 있는 문서 |
| --- | --- | --- |
| [docs/planning/](docs/planning/) | 기획 문서: 무엇을, 왜, 어떤 규칙으로 만드는지 | 프로젝트 소개, 시장 조사, 유사 프로그램 분석, 브레인스토밍, 이벤트 스토밍, 업무 분석, 요구사항 명세서, 정책 정의서, 기획안 |
| [docs/design/](docs/design/) | 설계 문서: 어떻게 만드는지 | DB 설계, DDL |
| [docs/issues/](docs/issues/) | 이슈로 올리기 전 초안, 작업분배표 | (스프린트 시작 때 채움) |

## 문서 위치 규칙

1. **기획 문서는 `docs/planning/`에 넣는다.** 요구사항·정책·업무 분석처럼 "무엇을 만들지"를 정하는 문서다.
2. **설계 문서는 `docs/design/`에 넣는다.** DB 설계, DDL, API 설계, 화면 설계처럼 "어떻게 만들지"를 정하는 문서다.
3. **문서에 딸린 그림은 그 문서가 있는 폴더의 `images/`에 넣는다.** 예: 기획 문서 그림은 `docs/planning/images/`.
4. **파일 이름은 `문서 이름(YYYY-MM-DD).확장자`로 쓴다.** 예: `DB 설계(2026-11-23).md`.
5. **어느 쪽인지 애매하면** PR 본문에 적고 리뷰에서 정한다. 새 종류의 폴더가 필요하면 이 표와 규칙을 같은 PR에서 고친다.

## ID 체계 (문서 · 이슈 · 코드가 같이 쓰는 이름)

| ID | 뜻 | 정의된 곳 |
| --- | --- | --- |
| `FR-업무-번호` | 기능 요구사항 (예: `FR-CNT-001`) | 요구사항 명세서 2장 |
| `NFR-종류-번호` | 비기능 요구사항 (예: `NFR-PERF-001`) | 요구사항 명세서 3장 |
| `POL-영역-번호` | 정책 (예: `POL-TGT-006`) | 정책 정의서 |
| `S-01`~`S-11` | 직원 화면 | 업무 분석 - 사용자, 피그마 |
| `B-01`~`B-05` | 백오피스 화면 (임시 ID) | 피그마 |

업무 코드: `AUTH` 로그인·권한 · `CUS` 보호자·반려동물 · `SRCH` 검색 · `VIS` 방문 기록 · `CYC` 주기 계산 · `TGT` 연락 대상 · `CNT` 연락 기록 · `DASH` 대시보드 · `RPT` 리포트 · `ADM` 백오피스 · `COM` 공통 화면 · `DATA` 시연·기본 데이터
