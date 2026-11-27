#!/usr/bin/env node
// 더미 데이터 CSV 점검 (#147). 더미 데이터 작성 매뉴얼 5장 점검표와 3장 상황을 CSV만으로 확인한다.
// 실행: node backend/scripts/dummy/check-dummy.mjs   (하나라도 틀리면 종료 코드 1)
//
// 누락률 · 재방문율 대조는 적재 뒤 #144 규칙으로 예정 일정을 만든 다음 SQL로 한다 (#157과 대조).

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { applyRuleSet, seedInt, seedSetting, seedVaccineRules } from './seed-settings.mjs';

const DIR = resolve(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/db/dummy');

function parseCsv(text) {
  const rows = [];
  let row = [], field = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') q = false;
      else field += c;
    } else if (c === '"') q = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; }
    else field += c;
  }
  const [head, ...body] = rows;
  return body.map((r) => Object.fromEntries(head.map((h, i) => [h, r[i]])));
}
const SEED = resolve(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/db/migration/V2__seed_base.sql');
const load = (n) => parseCsv(readFileSync(resolve(DIR, n), 'utf8'));
const staff = load('staff.csv');
const owners = load('owners.csv');
const pets = load('pets.csv');
const vac = load('vaccinations.csv');
const gro = load('groomings.csv');
const con = load('contacts.csv');

const results = [];
const check = (name, ok, detail = '') => results.push({ name, ok, detail });
const pct = (a, b) => `${((a / b) * 100).toFixed(1)}%`;
const between = (v, lo, hi) => v >= lo && v <= hi;
const END = '2026-11-25', START_BASE = '2024-07-01', CRM = seedSetting('CRM_START_DATE');
const RAINBOW_HOLD_DAYS = seedInt('RAINBOW_HOLD_DAYS'); // POL-TGT-003, 기본 데이터 setting

// ---- 양 (매뉴얼 2장 · 5장)
check('보호자 300명 안팎', between(owners.length, 280, 320), `${owners.length}`);
check('반려견 400마리 안팎', between(pets.length, 360, 440), `${pets.length}`);
check('직원 4~5명', between(staff.length, 4, 5), `${staff.length}`);
check('연락 기록 1,000건 안팎 (700~1,300)', between(con.length, 700, 1300), `${con.length}`);
const groomCount = {};
for (const g of gro) groomCount[g.pet_id] = (groomCount[g.pet_id] ?? 0) + 1;
const groomPets = Object.values(groomCount);
check('미용 기록 아이마다 10회 이하', groomPets.every((n) => n <= 10), `최대 ${Math.max(...groomPets)}`);

// ---- 고정값 (매뉴얼 4장, POL-DATA-009, T1)
const ENUM = {
  role: ['ADMIN', 'DESK', 'GROOMER'],
  coat: ['SHORT', 'MEDIUM', 'LONG', 'CURLY'],
  result: ['', 'BOOKED', 'NO_ANSWER', 'REFUSED', 'OTHER_HOSPITAL', 'WRONG_NUMBER'],
  method: ['PHONE', 'SMS'],
  vaccine: ['DHPPL', 'CORONA', 'KENNEL', 'RABIES', 'INFLUENZA', 'HEARTWORM', 'DEWORM'],
  status: ['ACTIVE', 'RAINBOW'],
  pref: ['', 'PHONE', 'SMS'],
};
check('역할 3가지 그대로', staff.every((s) => ENUM.role.includes(s.role)));
const seedBreeds = new Set([...readFileSync(SEED, 'utf8').matchAll(/\('DOG', '([^']+)', \d+\)/g)].map((m) => m[1]));
const unknownBreeds = [...new Set(pets.map((p) => p.breed_name))].filter((b) => !seedBreeds.has(b));
check('품종 이름이 V2 기본 데이터 품종 표에 있음 (#142)', seedBreeds.size > 0 && unknownBreeds.length === 0, unknownBreeds.join(', ') || `${seedBreeds.size}개 중에서 씀`);
check('털 유형 4가지 그대로', pets.every((p) => ENUM.coat.includes(p.coat_type_code)));
check('결과 유형 5가지 그대로', con.every((c) => ENUM.result.includes(c.result)));
check('연락 방법 그대로', con.every((c) => ENUM.method.includes(c.method)));
check('접종 종류 코드 그대로', vac.every((v) => ENUM.vaccine.includes(v.vaccine_type_code)));
check('아이 상태 그대로', pets.every((p) => ENUM.status.includes(p.status)));
check('선호 연락 방법 그대로', owners.every((o) => ENUM.pref.includes(o.preferred_contact)));

// ---- 개인정보 (POL-DATA-001)
const phoneOk = (p) => p === '' || /^0100\d{7}$/.test(p);
check('전화번호는 010-0000-0000 ~ 010-0999-9999 범위', owners.every((o) => phoneOk(o.phone) && phoneOk(o.sub_phone))
  && vac.every((v) => phoneOk(v.brought_by_phone)) && gro.every((g) => phoneOk(g.brought_by_phone)));
check('직원 CSV에 비밀번호 칸 없음', !Object.keys(staff[0]).some((k) => /pass/i.test(k)));

// ---- 3-1 구성
const petsByOwner = {};
for (const p of pets) (petsByOwner[p.guardian_id] ??= []).push(p);
const multi = Object.values(petsByOwner).filter((a) => a.length >= 2).length;
check('다두 가구 약 25%', between(multi / owners.length, 0.2, 0.3), pct(multi, owners.length));
const nameCount = {};
for (const p of pets) nameCount[p.name] = (nameCount[p.name] ?? 0) + 1;
const fivePlus = Object.entries(nameCount).filter(([, n]) => n >= 5);
check('같은 이름 5마리 이상인 이름 3개 이상', fivePlus.length >= 3, fivePlus.map(([n, c]) => `${n} ${c}`).slice(0, 6).join(', '));
const last4 = {};
for (const o of owners) (last4[o.phone.slice(-4)] ??= []).push(o);
const sameLast4 = Object.values(last4).filter((a) => a.length >= 2).length;
check('같은 전화번호 뒤 4자리 3쌍 이상', sameLast4 >= 3, `${sameLast4}쌍`);
const est = pets.filter((p) => p.birth_date_estimated === '1').length;
check('추정 생년월일 약 5%', between(est / pets.length, 0.02, 0.08), pct(est, pets.length));
const rainbow = pets.filter((p) => p.status === 'RAINBOW');
const rainbowMulti = rainbow.filter((p) => petsByOwner[p.guardian_id].length >= 2).length;
check('무지개다리 5~10마리', between(rainbow.length, 5, 10), `${rainbow.length}`);
check('그중 2마리는 같은 집에 다른 아이', rainbowMulti >= 2, `${rainbowMulti}`);
check('무지개다리면 rainbow_at 있음 (DB CHECK)', pets.every((p) => (p.status === 'RAINBOW') === (p.rainbow_at !== '')));
const noContact = owners.filter((o) => o.tag_no_contact === '1').length;
check("'연락 원치 않음' 5~10명", between(noContact, 5, 10), `${noContact}`);
const wrong = owners.filter((o) => o.phone_status === 'WRONG').length;
check('번호 오류 10명 안팎', between(wrong, 7, 13), `${wrong}`);
const mix = pets.filter((p) => p.breed_name === '믹스').length;
check('믹스견 약 15%', between(mix / pets.length, 0.1, 0.2), pct(mix, pets.length));
const noSms = owners.filter((o) => o.sms_agreed === '0').length;
check('안내 문자 수신 동의 없음 약 10% (POL-DATA-003)', between(noSms / owners.length, 0.07, 0.13), pct(noSms, owners.length));
const smsToNoAgree = con.filter((c) => c.method === 'SMS' && owners[+c.guardian_id - 1].sms_agreed === '0').length;
check('수신 동의 없는 보호자에게 문자 없음', smsToNoAgree === 0, `${smsToNoAgree}`);
const contactNoContact = con.filter((c) => owners[+c.guardian_id - 1].tag_no_contact === '1').length;
check("'연락 원치 않음' 보호자에게 연락 없음", contactNoContact === 0, `${contactNoContact}`);

// ---- 3-2 방문 패턴 (미용)
const groomers = pets.filter((p) => groomCount[p.pet_id] !== undefined || p.coat_type_code === 'LONG' || p.coat_type_code === 'CURLY');
const few = groomers.filter((p) => (groomCount[p.pet_id] ?? 0) <= 1).length;
check('미용 기록 0~1회 아이 있음 (약 15%)', few > 0, pct(few, groomers.length));
check('미용 3회 이상 아이 있음', groomPets.filter((n) => n >= 3).length > 0, `${groomPets.filter((n) => n >= 3).length}마리`);

// ---- 3-3 연락 결과 분포 (결과가 있는 행)
const withResult = con.filter((c) => c.result !== '');
const dist = {};
for (const c of withResult) dist[c.result] = (dist[c.result] ?? 0) + 1;
check('결과 유형 5가지가 모두 있음', ENUM.result.slice(1).every((r) => dist[r] > 0),
  Object.entries(dist).map(([r, n]) => `${r} ${pct(n, withResult.length)}`).join(', '));
const naFollow = con.filter((c) => c.memo === '2일차').length;
check("'전화 안 받음' 다음 날 2일차 연락 있음", naFollow > 0, `${naFollow}건`);
const groups = Object.values(con.reduce((m, c) => ((m[c.contact_group_id] ??= []).push(c), m), {}));
const multiGroups = groups.filter((g) => new Set(g.map((c) => c.pet_id)).size >= 2);
check('다두 가구 묶음 연락 (한 연락에 같은 집 아이 2마리 이상) 1건 이상 (매뉴얼 3-1 · 3-3)', multiGroups.length >= 1, `${multiGroups.length}묶음`);
check('묶음 연락의 행은 같은 보호자 · 연락 시각 · 방법 · 결과', groups.every((g) => g.every((c) => c.guardian_id === g[0].guardian_id
  && c.contacted_at === g[0].contacted_at && c.method === g[0].method && c.result === g[0].result)));
check('문자 모의 발송은 문구 원문이 있음 (DB CHECK)', con.every((c) => c.is_mock_sms === '0' || (c.method === 'SMS' && c.sms_body !== '')));
check('전화면 결과가 있음 (DB CHECK)', con.every((c) => c.method === 'SMS' || c.result !== ''));

// ---- 3-4 도입 전 · 후
const conBefore = con.filter((c) => c.contacted_at < CRM).length;
check('도입 전 연락 기록은 거의 없음 (10% 미만)', conBefore / con.length < 0.1, `${conBefore}건 / ${con.length}`);

// ---- 접종 규칙 읽기 (생성 스크립트가 앱과 같은 규칙을 쓰는지, PR #226 리뷰)
const rules = seedVaccineRules();
check('마이그레이션 접종 규칙을 읽음 (광견병 첫 예정 112일 등)', rules.RABIES?.[0]?.from === 112 && rules.DHPPL?.length === 2,
  Object.entries(rules).map(([c, r]) => `${c} ${r.length}행`).join(', '));
const rejects = (set) => { try { applyRuleSet({}, set); return false; } catch { return true; } };
check('접종 규칙 UPDATE에 모르는 칸이 있으면 멈춤', !rejects("r.age_from_days = 112, r.note = 'x'") && rejects("r.age_from_days = 1, r.phase = 'X'"));

// ---- 날짜 (매뉴얼 4장 · 5장)
const petMap = new Map(pets.map((p) => [p.pet_id, p]));
const dateOf = (r) => r.visit_date ?? r.contacted_at.slice(0, 10);
const bad = [...vac, ...gro, ...con].filter((r) => {
  const p = petMap.get(r.pet_id);
  const d = dateOf(r);
  return d < p.birth_date || (p.rainbow_at && d >= p.rainbow_at.slice(0, 10)) || d > END;
});
check('생년월일 전 · 무지개다리 뒤 · 미래 기록 없음', bad.length === 0, `${bad.length}건`);
// 등록 전 과거 기록(등록할 때 옮겨 적은 접종 이력)은 그 전이어도 된다. 예정일 계산이 기초접종을 다 채운 것으로 보게 하려는 것 (K24, #147)
const regOf = (r) => petMap.get(r.pet_id).created_at.slice(0, 10);
const early = [...vac, ...gro].filter((r) => dateOf(r) < START_BASE && dateOf(r) >= regOf(r)).length;
check('등록 뒤 기록은 기간 직전 기준 접종부터 (2024-07-01 이후)', early === 0, `${early}건`);

// ---- 무지개다리 보류 (POL-TGT-003): 같은 집 아이는 건넌 날부터 RAINBOW_HOLD_DAYS일 동안 연락하지 않는다
const holdHit = con.filter((c) => rainbow.some((r) => r.guardian_id === c.guardian_id && r.pet_id !== c.pet_id
  && c.contacted_at.slice(0, 10) >= r.rainbow_at.slice(0, 10)
  && c.contacted_at.slice(0, 10) <= new Date(Date.parse(r.rainbow_at.slice(0, 10)) + RAINBOW_HOLD_DAYS * 864e5).toISOString().slice(0, 10))).length;
check('무지개다리 보류 기간 안에 같은 집 아이 연락 없음', holdHit === 0, `${holdHit}건`);

// ---- FK
const ownerIds = new Set(owners.map((o) => o.guardian_id));
const loginIds = new Set(staff.map((s) => s.login_id));
check('아이 → 보호자 연결', pets.every((p) => ownerIds.has(p.guardian_id)));
check('기록 → 아이 연결', [...vac, ...gro, ...con].every((r) => petMap.has(r.pet_id)));
check('연락 → 보호자가 그 아이의 보호자', con.every((c) => petMap.get(c.pet_id).guardian_id === c.guardian_id));
check('기록한 직원이 직원 CSV에 있음', [...owners, ...pets, ...vac, ...gro, ...con].every((r) => loginIds.has(r.created_by_login_id)));
check('미용 기록은 미용사, 연락 기록은 데스크가 남김', gro.every((g) => staff.find((s) => s.login_id === g.created_by_login_id).role === 'GROOMER')
  && con.every((c) => staff.find((s) => s.login_id === c.created_by_login_id).role !== 'GROOMER'));

// ---- 출력
let fail = 0;
for (const r of results) {
  if (!r.ok) fail++;
  console.log(`${r.ok ? 'OK  ' : 'FAIL'} ${r.name}${r.detail ? `  (${r.detail})` : ''}`);
}
console.log(`\n${results.length - fail}/${results.length} 통과`);
process.exit(fail ? 1 : 0);
