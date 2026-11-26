# 시연용 더미 데이터 (#147)

local · demo 프로필에서만 적재한다 (K8). 운영 DB에는 넣지 않는다.

## 만들기 · 점검

```bash
node backend/scripts/dummy/generate-dummy.mjs   # CSV 6개를 이 폴더에 다시 만든다 (시드 고정, 매번 같은 결과)
node backend/scripts/dummy/check-dummy.mjs      # 더미 데이터 작성 매뉴얼 5장 점검표 확인 (틀리면 종료 코드 1)
```

CSV는 손으로 고치지 않는다. 값을 바꾸려면 생성 스크립트를 고치고 다시 만든다.

## 기간

- 기록 기간: 2025-06-01 ~ 2026-11-25 (기간 직전 기준 접종은 2024-07-01부터)
- CRM 도입일: 2026-06-01. 도입 전에는 연락 기록이 거의 없고 누락이 많다. 도입 뒤에는 연락 기록이 쌓이고 누락이 준다.
- 미래 날짜 기록은 없다.

## 파일과 참조 규칙

| 파일 | 행 | 참조 |
| --- | --- | --- |
| `staff.csv` | 직원 5명 | `login_id`로 참조한다. 비밀번호는 없다. 적재기가 시연용 비밀번호를 넣는다. |
| `owners.csv` | 보호자 | `guardian_id`는 CSV 안에서만 쓰는 번호(1부터)다. DB id와 같다고 보지 않는다. |
| `pets.csv` | 반려견 | `breed_name`(품종 이름)과 `coat_type_code`로 기본 데이터를 찾는다. |
| `vaccinations.csv` | 접종 · 예방 방문 | `vaccine_type_code`로 접종 종류를 찾는다. |
| `groomings.csv` | 미용 방문 | |
| `contacts.csv` | 연락 기록 | `schedule_id` · `is_converted` 칸은 없다 (아래 참고). |

- 모든 표의 `created_by_login_id`는 `staff.csv`의 `login_id`다.
- 참/거짓은 `1` / `0`, 빈 값은 빈 칸이다. 날짜는 `YYYY-MM-DD`, 일시는 `YYYY-MM-DD HH:MM:SS`(KST).
- 전화번호는 `0100xxxxxxx` 형식이며 010-0000-0000 ~ 010-0999-9999 범위만 쓴다 (POL-DATA-001).

## 적재기가 할 일

1. 직원 → 보호자 → 반려견 → 접종 · 미용 기록 순서로 넣는다. `guardian_id` · `pet_id`는 CSV 안 번호라, 넣을 때 DB가 준 id를 CSV 번호별로 기록해 두고 반려견 · 기록 · 연락의 참조를 그 id로 바꿔 넣는다. local · demo DB가 비어 있다고 가정하지 않는다 (이미 보호자가 있거나 자동 증가 번호가 진행됐을 수 있음).
2. #144 규칙으로 예정 일정을 만들고, 기록 날짜 기준으로 누락 판정을 돌린다 (`missed_at` = 원래 판정일).
3. 연락 기록은 `pet_id` · `service_type` · `vaccine_type_code`와 연락 시각으로 그때 열린 예정 일정을 찾아 `schedule_id`를 붙인다.
4. `is_converted`는 #154 전환 판정 규칙으로 채운다.

## 확인할 것

- `pets.csv`의 품종 이름은 V2 기본 데이터(`V2__seed_base.sql` 품종 표, #142)에 있는 이름만 쓴다. 점검 스크립트가 확인한다.
- 적재기 위치(Java `CommandLineRunner` 또는 SQL)는 #142 뒤에 정한다.
