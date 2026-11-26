#!/usr/bin/env node
// 시연용 더미 데이터 CSV 만들기 (#147, FR-DATA-001)
// 근거: docs/planning/더미 데이터 작성 매뉴얼(2026-11-23).md 2 · 3 · 4장, DDL(2026-11-23).sql, 팀 결정 K8 · T1 · T15 · K23 · K24
//
// 실행: node backend/scripts/dummy/generate-dummy.mjs
//   → backend/src/main/resources/db/dummy/*.csv 6개를 다시 만든다.
//   씨앗(SEED)이 고정이라 몇 번을 돌려도 같은 파일이 나온다.
//
// 실제 사람 · 병원 정보는 넣지 않는다. 전화번호는 010-0000-0000 ~ 010-0999-9999 범위만 쓴다 (POL-DATA-001).

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { formatPhone, seedCoatGroomDays, seedInt, seedSetting } from './seed-settings.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(HERE, '../../src/main/resources/db/dummy');

// ---------------------------------------------------------------------
// 설정
// ---------------------------------------------------------------------
const SEED = 20261126;
const START = '2025-06-01';           // 데이터 기간 시작 (매뉴얼 2장)
const CRM_START = seedSetting('CRM_START_DATE'); // CRM 도입일 (매뉴얼 3-4, V2 기본 데이터)
// 정책 기준값은 기본 데이터(setting)에서 읽는다 (CLAUDE.md 3번)
const MISS_THRESHOLD_DAYS = seedInt('MISS_THRESHOLD_DAYS'); // 누락 기준 (POL-MISS-001)
const RAINBOW_HOLD_DAYS = seedInt('RAINBOW_HOLD_DAYS');     // 무지개다리 같은 집 보류 (POL-TGT-003)
const END = '2026-11-25';             // 데이터 마지막 날 (오늘 전날까지, 미래 기록 없음)
const OWNER_COUNT = 300;
const HOSPITAL_NAME = seedSetting('HOSPITAL_NAME');                     // V1 기본 데이터 setting (문자 문구에 씀)
const HOSPITAL_PHONE_TEXT = formatPhone(seedSetting('HOSPITAL_PHONE')); // V1 기본 데이터 setting (시연용 번호)

// 털 유형 기본 간격(일) = coat_type.default_groom_weeks × 7 (POL-CYC-002, V1 기본 데이터)
const COAT_DEFAULT_DAYS = seedCoatGroomDays();

// 접종 종류 (DB 설계 4-6 vaccine_type.code). 주요 접종: 종합백신 · 광견병 (#59)
const VACCINE_NAME = {
  DHPPL: '종합백신', CORONA: '코로나 장염', KENNEL: '켄넬코프', RABIES: '광견병',
  INFLUENZA: '인플루엔자', HEARTWORM: '심장사상충 예방약', DEWORM: '구충제',
};

// 누락 경향 (매뉴얼 3-2 · 3-4): 도입 전 30% 안팎, 도입 뒤 15~20%, 늦었지만 14일 안 15%
const MISS_RATE_BEFORE = 0.30;
const MISS_RATE_AFTER = 0.17;
const LATE_WITHIN_RATE = 0.15;
const CONTACT_THIS_WEEK_SHARE = 0.8; // 도입 뒤 제때 올 아이 중 예정일 전에 미리 연락한 비율 (이번 주 탭)
const REGULAR_GROOMER_MISS_FACTOR = 0.55; // 미용 꼬박꼬박 오는 아이는 접종 누락이 낮다 (가설 3)

// 연락 결과 비율과 전환 목표 (매뉴얼 3-3)
const RESULT_WEIGHTS = [
  ['BOOKED', 35], ['NO_ANSWER', 30], ['REFUSED', 15], ['OTHER_HOSPITAL', 10], ['WRONG_NUMBER', 10],
];
const CONVERT_RATE = {
  BOOKED: 0.85, REFUSED: 0.07, OTHER_HOSPITAL: 0.03, WRONG_NUMBER: 0,
  NO_ANSWER_PHONE: 0.25, NO_ANSWER_SMS: 0.45,
};

// ---------------------------------------------------------------------
// 난수 · 날짜 도구
// ---------------------------------------------------------------------
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rnd = mulberry32(SEED);
const chance = (p) => rnd() < p;
const randInt = (lo, hi) => lo + Math.floor(rnd() * (hi - lo + 1));
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
function weighted(pairs) {
  const total = pairs.reduce((s, [, w]) => s + w, 0);
  let r = rnd() * total;
  for (const [v, w] of pairs) { if ((r -= w) < 0) return v; }
  return pairs[pairs.length - 1][0];
}
function shuffle(arr) {
  for (let i = arr.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [arr[i], arr[j]] = [arr[j], arr[i]]; }
  return arr;
}

const DAY = 86400000;
const toMs = (s) => Date.UTC(+s.slice(0, 4), +s.slice(5, 7) - 1, +s.slice(8, 10));
const fromMs = (ms) => new Date(ms).toISOString().slice(0, 10);
const addDays = (s, n) => fromMs(toMs(s) + n * DAY);
const diffDays = (a, b) => Math.round((toMs(b) - toMs(a)) / DAY); // b - a
const before = (a, b) => toMs(a) < toMs(b);
const minDate = (a, b) => (before(a, b) ? a : b);
const maxDate = (a, b) => (before(a, b) ? b : a);
const randDate = (lo, hi) => addDays(lo, randInt(0, Math.max(0, diffDays(lo, hi))));
const time = (d, h, m) => `${d} ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:00`;
const workTime = (d) => time(d, randInt(9, 18), pick([0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55]));

let uuidCounter = 0;
function uuid() { // 결정적 UUID 모양 문자열 (contact_group_id)
  uuidCounter++;
  const h = (n, len) => Math.floor(rnd() * 16 ** len).toString(16).padStart(len, '0') + '';
  return `${h(0, 8)}-${h(0, 4)}-4${h(0, 3)}-a${h(0, 3)}-${String(uuidCounter).padStart(12, '0')}`;
}

// ---------------------------------------------------------------------
// 이름 · 번호 (지어낸 값)
// ---------------------------------------------------------------------
const SURNAMES = ['김', '이', '박', '최', '정', '강', '조', '윤', '장', '임', '한', '오', '서', '신', '권', '황', '안', '송', '류', '홍'];
const GIVEN = ['민준', '서연', '지우', '하은', '도윤', '예준', '수아', '지호', '서준', '하린', '시우', '지아', '주원', '윤서', '건우',
  '채원', '우진', '다은', '현우', '수빈', '정희', '영숙', '순자', '경자', '미경', '은영', '성호', '영수', '상철', '혜진', '지훈', '유진'];
const PET_NAMES_COMMON = ['초코', '콩이', '보리'];            // 같은 이름 5마리 이상 (매뉴얼 3-1)
const PET_NAMES = ['두부', '코코', '몽이', '별이', '해피', '루이', '뭉치', '까미', '쿠키', '하루', '똘이', '사랑', '단비', '밤이',
  '호두', '망고', '모카', '라떼', '구름', '솜이', '찰떡', '복실', '봄이', '여름', '가을', '겨울', '토리', '마루', '감자', '자두',
  '체리', '레오', '맥스', '벨라', '루루', '나나', '보노', '다롱', '꼬미', '알밤', '탄이', '설이', '방울', '도치', '깜지', '흰둥'];

// 품종 (이름 · 기본 털 유형 · 체중 범위kg). 품종 선택지는 #142 시드가 정한다. 여기 이름이 시드와 같아야 적재된다
// 품종 이름은 V2__seed_base.sql의 breed 표와 같아야 한다 (#142 머지본). 점검 스크립트가 확인한다.
const BREEDS = [
  ['말티즈', 'LONG', 2, 4], ['푸들', 'CURLY', 2, 6], ['포메라니안', 'LONG', 2, 3.5], ['시츄', 'LONG', 4, 7],
  ['비숑 프리제', 'CURLY', 4, 7], ['요크셔 테리어', 'LONG', 2, 3], ['치와와', 'SHORT', 1.5, 3], ['웰시 코기', 'MEDIUM', 10, 14],
  ['골든 리트리버', 'LONG', 25, 34], ['진돗개', 'MEDIUM', 15, 23], ['시바견', 'MEDIUM', 8, 11], ['닥스훈트', 'SHORT', 5, 9],
  ['프렌치 불독', 'SHORT', 9, 13], ['비글', 'SHORT', 9, 13], ['미니어처 슈나우저', 'MEDIUM', 5, 8], ['보더 콜리', 'LONG', 14, 20],
  ['래브라도 리트리버', 'SHORT', 25, 35], ['스피츠', 'LONG', 5, 10], ['페키니즈', 'LONG', 3, 6], ['퍼그', 'SHORT', 6, 9],
  ['사모예드', 'LONG', 18, 28], ['셔틀랜드 쉽독', 'LONG', 6, 11],
];
const MIX = ['믹스', null, 3, 15];

// 010-0ABC-DEFG 꼴 11자리 (실제로 쓰지 않는 범위)
const usedPhones = new Set();
function newPhone(last4) {
  for (;;) {
    const mid = String(randInt(0, 999)).padStart(3, '0');
    const tail = last4 ?? String(randInt(0, 9999)).padStart(4, '0');
    const p = `0100${mid}${tail}`;
    if (!usedPhones.has(p)) { usedPhones.add(p); return p; }
  }
}

// ---------------------------------------------------------------------
// 직원 (staff.csv). 비밀번호는 넣지 않는다 (환경 변수, DB 설계 8장)
// ---------------------------------------------------------------------
const staff = [
  { login_id: 'demo_admin', name: '정원장', role: 'ADMIN', is_active: 1 },
  { login_id: 'demo_lead', name: '한실장', role: 'ADMIN', is_active: 1 },
  { login_id: 'demo_desk1', name: '오데스크', role: 'DESK', is_active: 1 },
  { login_id: 'demo_desk2', name: '서데스크', role: 'DESK', is_active: 1 },
  { login_id: 'demo_groomer', name: '윤미용', role: 'GROOMER', is_active: 1 },
];
const DESKS = ['demo_desk1', 'demo_desk2', 'demo_lead'];
const VET_RECORDERS = ['demo_desk1', 'demo_desk2'];
const GROOMER = 'demo_groomer';

// ---------------------------------------------------------------------
// 보호자 (owners.csv → guardian)
// ---------------------------------------------------------------------
const owners = [];
for (let i = 1; i <= OWNER_COUNT; i++) {
  const regDate = randDate('2023-06-01', addDays(END, -40));
  owners.push({
    guardian_id: i,
    name: pick(SURNAMES) + pick(GIVEN),
    phone: null,
    sub_phone: null,
    preferred_contact: weighted([['PHONE', 40], ['SMS', 45], ['', 15]]),
    phone_status: 'OK',
    phone_checked_at: regDate,
    privacy_agreed: 1,
    privacy_agreed_at: workTime(regDate),
    sms_agreed: 1,
    sms_agreed_at: workTime(regDate),
    tag_no_contact: 0, tag_sms_only: 0, tag_sensitive: 0, tag_contact_time: 0, contact_time_note: '',
    created_by_login_id: pick(DESKS),
    created_at: workTime(regDate),
    _reg: regDate,
  });
}
// 같은 뒤 4자리 3쌍 이상 (매뉴얼 3-1): 4쌍
const sharedLast4 = ['1234', '5678', '2580', '7777'];
const pairOwners = shuffle(owners.slice()).slice(0, 8);
pairOwners.forEach((o, i) => { o.phone = newPhone(sharedLast4[Math.floor(i / 2)]); });
for (const o of owners) { if (!o.phone) o.phone = newPhone(); }
for (const o of owners) { if (chance(0.25)) o.sub_phone = newPhone(); }
// 안내 문자 수신 동의 없음 약 10% (POL-DATA-003)
for (const o of shuffle(owners.slice()).slice(0, 30)) { o.sms_agreed = 0; o.sms_agreed_at = ''; if (o.preferred_contact === 'SMS') o.preferred_contact = 'PHONE'; }
// 번호 오류 10명 안팎 (FR-DASH-004)
const wrongOwners = shuffle(owners.filter((o) => o.sms_agreed === 1)).slice(0, 10);
for (const o of wrongOwners) { o.phone_status = 'WRONG'; }
// '연락 원치 않음' 7명 (POL-TGT-004)
const noContactOwners = shuffle(owners.filter((o) => o.phone_status === 'OK')).slice(0, 7);
for (const o of noContactOwners) o.tag_no_contact = 1;
// 다른 주의 태그 (FR-CUS-005)
for (const o of owners) {
  if (o.sms_agreed && !o.tag_no_contact && chance(0.05)) { o.tag_sms_only = 1; o.preferred_contact = 'SMS'; }
  if (chance(0.06)) o.tag_sensitive = 1;
  if (chance(0.05)) { o.tag_contact_time = 1; o.contact_time_note = pick(['오후 2시 이후', '오전만', '점심시간 피해서', '저녁 6시 이후']); }
}
// 번호 마지막 확인일: 일부는 1년이 넘어 배너가 뜨게 (POL-REG-008)
for (const o of owners) {
  if (before(o._reg, '2025-09-01') && chance(0.6)) o.phone_checked_at = randDate(maxDate(o._reg, '2025-01-01'), '2026-11-01');
}

// ---------------------------------------------------------------------
// 반려동물 (pets.csv → pet)
// ---------------------------------------------------------------------
const pets = [];
let petId = 0;
const nameCount = {};
function petName() {
  // 흔한 이름 3개는 각각 6~7마리가 되게 먼저 채운다
  for (const n of PET_NAMES_COMMON) { if ((nameCount[n] ?? 0) < 6 && chance(0.04)) { nameCount[n] = (nameCount[n] ?? 0) + 1; return n; } }
  const n = pick(PET_NAMES); nameCount[n] = (nameCount[n] ?? 0) + 1; return n;
}
const multiOwners = new Set(shuffle(owners.slice()).slice(0, 75).map((o) => o.guardian_id)); // 다두 가구 25%
for (const o of owners) {
  const count = multiOwners.has(o.guardian_id) ? (chance(0.25) ? 3 : 2) : 1;
  for (let k = 0; k < count; k++) {
    petId++;
    const isMix = chance(0.15);
    const [breed, coat0, wLo, wHi] = isMix ? MIX : pick(BREEDS);
    const coat = isMix ? weighted([['SHORT', 3], ['MEDIUM', 4], ['LONG', 2], ['CURLY', 1]]) : (chance(0.08) ? pick(['SHORT', 'MEDIUM', 'LONG', 'CURLY']) : coat0);
    // 나이: 어린 강아지(데이터 기간 안 출생) 약 20%, 나머지 1~15살
    const puppy = chance(0.2);
    const birth = puppy ? randDate('2025-03-01', addDays(END, -60)) : randDate('2011-01-01', '2024-04-30');
    const reg = puppy ? addDays(birth, randInt(40, 60)) : maxDate(o._reg, addDays(birth, 60));
    const estimated = !puppy && chance(0.05) ? 1 : 0; // 추정 생년월일 약 5% (POL-REG-004)
    pets.push({
      pet_id: petId,
      guardian_id: o.guardian_id,
      name: petName(),
      species: 'DOG',
      breed_name: breed,
      coat_type_code: coat,
      birth_date: estimated ? birth.slice(0, 8) + '01' : birth,
      birth_date_estimated: estimated,
      registration_no: chance(0.6) ? `D${String(petId).padStart(6, '0')}` : '',
      has_pet_insurance: weighted([['1', 15], ['0', 45], ['', 40]]),
      weight_kg: (wLo + rnd() * (wHi - wLo)).toFixed(1),
      status: 'ACTIVE',
      rainbow_at: '',
      contact_hold_until: '',
      contact_hold_by_pet_id: '',
      created_by_login_id: pick(DESKS),
      created_at: workTime(maxDate(reg, o._reg)),
      _reg: maxDate(reg, o._reg),
      _puppy: puppy,
      _owner: o,
    });
  }
}
// 같은 이름 5마리 이상인 이름 3개 이상 보장 (매뉴얼 3-1)
for (const n of PET_NAMES_COMMON) {
  const have = pets.filter((p) => p.name === n).length;
  const others = shuffle(pets.filter((p) => !PET_NAMES_COMMON.includes(p.name))).slice(0, Math.max(0, 6 - have));
  for (const p of others) p.name = n;
}

// 무지개다리 8마리, 그중 2마리는 같은 집에 다른 아이 있음 (매뉴얼 3-1, POL-END-001 · 003)
const rainbowPets = [];
const inMulti = shuffle(pets.filter((p) => multiOwners.has(p.guardian_id) && !p._puppy)).slice(0, 2);
const single = shuffle(pets.filter((p) => !multiOwners.has(p.guardian_id) && !p._puppy)).slice(0, 6);
for (const p of [...inMulti, ...single]) {
  const at = p === inMulti[0] ? addDays(END, -12) : randDate('2025-09-01', addDays(END, -45)); // 1마리는 최근이라 같은 집 보류가 아직 걸림
  p.status = 'RAINBOW';
  p.rainbow_at = time(at, randInt(10, 17), 0);
  p._rainbow = at;
  rainbowPets.push(p);
  if (multiOwners.has(p.guardian_id)) {
    const holdUntil = addDays(at, RAINBOW_HOLD_DAYS); // POL-TGT-003
    for (const s of pets.filter((x) => x.guardian_id === p.guardian_id && x !== p)) {
      (s._holds ??= []).push({ from: at, until: holdUntil }); // 생성 중에는 보류 기간을 늘 기억한다
      if (!before(holdUntil, END)) { s.contact_hold_until = holdUntil; s.contact_hold_by_pet_id = p.pet_id; } // CSV에는 END에 아직 걸린 보류만
    }
  }
}

// ---------------------------------------------------------------------
// 미용 패턴 (매뉴얼 3-2): 일정 40%, 들쭉날쭉 20%, 0~1회 15%, 보통 25%
// ---------------------------------------------------------------------
for (const p of pets) {
  const groomer = p.coat_type_code === 'LONG' || p.coat_type_code === 'CURLY' ? chance(0.85) : chance(0.35);
  p._groom = groomer ? weighted([['REGULAR', 40], ['JUMPY', 20], ['FEW', 15], ['NORMAL', 25]]) : null;
}

// ---------------------------------------------------------------------
// 방문 · 연락 만들기
// ---------------------------------------------------------------------
const vaccinations = [];
const groomings = [];
const contacts = [];

const alive = (p, d) => !p._rainbow || before(d, p._rainbow);
const inData = (d) => !before(d, START) && !before(END, d);
const canContact = (o) => !o.tag_no_contact;
/** 같은 집 아이가 무지개다리를 건넌 날부터 보류 끝날(포함)까지는 연락하지 않는다 (POL-TGT-003, RAINBOW_HOLD_DAYS) */
const onHold = (p, d) => (p._holds ?? []).some((h) => !before(d, h.from) && !before(h.until, d));

function addVaccination(p, code, d) {
  vaccinations.push({
    pet_id: p.pet_id, vaccine_type_code: code, visit_date: d,
    memo: chance(0.08) ? pick(['기침 조금 있음', '접종 후 30분 관찰', '체중 늘었음', '다음엔 오후 방문 희망']) : '',
    brought_by_phone: p._owner.sub_phone && chance(0.15) ? p._owner.sub_phone : '',
    created_by_login_id: pick(VET_RECORDERS),
    created_at: workTime(d),
  });
}
function addGrooming(p, d) {
  groomings.push({
    pet_id: p.pet_id, visit_date: d,
    memo: chance(0.15) ? pick(['발톱 짧게', '얼굴 둥글게', '귀 털 정리', '예민해서 천천히', '목욕만']) : '',
    brought_by_phone: p._owner.sub_phone && chance(0.15) ? p._owner.sub_phone : '',
    created_by_login_id: GROOMER,
    created_at: workTime(d),
  });
}

function smsBody(service, p, due, code) {
  if (service === 'VACCINE') {
    return `[${HOSPITAL_NAME}] ${p.name}의 ${VACCINE_NAME[code]} 접종 예정일은 ${due}입니다. 방문 전 ${HOSPITAL_PHONE_TEXT}로 예약해 주세요.`;
  }
  return `[${HOSPITAL_NAME}] ${p.name}의 다음 미용 예정일은 ${due} 무렵입니다. 원하시는 날짜를 ${HOSPITAL_PHONE_TEXT}로 알려 주세요.`;
}

/**
 * 연락 한 번 (묶음). 결과를 정하고 기록을 남긴다. 전환 여부(재방문 확률)를 돌려준다.
 * 전환 여부 자체(is_converted)는 CSV에 넣지 않는다. 적재 뒤 #154 규칙(DB 설계 5-3)으로 채운다.
 */
function contactFor(p, service, code, due, day) {
  const o = p._owner;
  const items = [{ p, code }];
  const groupId = uuid();
  const desk = pick(DESKS);
  let result;
  let method;
  if (o.phone_status === 'WRONG') { method = 'PHONE'; result = 'WRONG_NUMBER'; }
  else if (o.tag_sms_only) { method = 'SMS'; result = null; }
  else {
    method = o.preferred_contact === 'SMS' && o.sms_agreed ? (chance(0.7) ? 'SMS' : 'PHONE') : 'PHONE';
    result = method === 'PHONE' ? weighted(RESULT_WEIGHTS.filter(([r]) => r !== 'WRONG_NUMBER')) : (chance(0.4) ? 'BOOKED' : null);
  }
  const rows = (m, r, d, note) => {
    for (const it of items) {
      contacts.push({
        contact_group_id: groupId,
        guardian_id: o.guardian_id,
        pet_id: it.p.pet_id,
        service_type: service,
        vaccine_type_code: it.code ?? '',
        method: m,
        result: r ?? '',
        is_mock_sms: m === 'SMS' ? 1 : 0,
        sms_template: m === 'SMS' ? service : '',
        sms_body: m === 'SMS' ? smsBody(service, it.p, due, it.code) : '',
        talked_with: m === 'PHONE' && r && r !== 'NO_ANSWER' && r !== 'WRONG_NUMBER' ? (chance(0.85) ? 'SELF' : 'FAMILY') : '',
        memo: note ?? '',
        next_contact_date: r === 'NO_ANSWER' ? addDays(d, 1) : '',
        contacted_at: workTime(d),
        created_by_login_id: desk,
      });
    }
  };
  if (method === 'PHONE' && result === 'NO_ANSWER') {
    rows('PHONE', 'NO_ANSWER', day);
    // 다음 날 2일차 (POL-TGT-006, 매뉴얼 3-3: 2일차까지만)
    const d2 = addDays(day, 1);
    if (before(END, d2) || onHold(p, d2)) return 0;
    const follow = o.sms_agreed && chance(0.5) ? 'SMS' : 'PHONE';
    const groupId2 = uuid();
    const r2 = follow === 'PHONE' ? (chance(0.6) ? 'NO_ANSWER' : 'BOOKED') : null;
    for (const it of items) {
      contacts.push({
        contact_group_id: groupId2, guardian_id: o.guardian_id, pet_id: it.p.pet_id, service_type: service,
        vaccine_type_code: it.code ?? '', method: follow, result: r2 ?? '', is_mock_sms: follow === 'SMS' ? 1 : 0,
        sms_template: follow === 'SMS' ? service : '', sms_body: follow === 'SMS' ? smsBody(service, it.p, due, it.code) : '',
        talked_with: r2 === 'BOOKED' ? 'SELF' : '', memo: '2일차', next_contact_date: '',
        contacted_at: workTime(d2), created_by_login_id: desk,
      });
    }
    if (r2 === 'BOOKED') return CONVERT_RATE.BOOKED;
    return follow === 'SMS' ? CONVERT_RATE.NO_ANSWER_SMS : CONVERT_RATE.NO_ANSWER_PHONE;
  }
  rows(method, result, day, result === 'REFUSED' ? pick(['다음에 연락 달라고 함', '당분간 안 온다고 함', '']) : '');
  if (result === null) return CONVERT_RATE.NO_ANSWER_SMS; // 문자만 보내고 답 없음
  return CONVERT_RATE[result];
}

/**
 * 예정 하나(due)에 대한 실제 방문일을 정한다. 기간 밖이거나 안 오면 null.
 * 도입 뒤 예정은 제때 안 왔으면 연락하고, 연락 결과에 따라 기준(14일) 안에 오게 한다.
 */
function outcome(p, service, code, due, missBase) {
  const after = !before(due, CRM_START);
  let missRate = after ? MISS_RATE_AFTER : MISS_RATE_BEFORE;
  missRate *= missBase;
  if (p._groom === 'REGULAR' && service === 'VACCINE') missRate *= REGULAR_GROOMER_MISS_FACTOR;
  const r = rnd();
  let visit;
  if (r < missRate) visit = chance(0.55) ? addDays(due, randInt(MISS_THRESHOLD_DAYS + 1, 90)) : null; // 누락 (늦게 오거나 안 옴)
  else if (r < missRate + LATE_WITHIN_RATE) visit = addDays(due, randInt(1, MISS_THRESHOLD_DAYS)); // 늦었지만 기준 안
  else visit = addDays(due, randInt(-7, 0)); // 제때

  // 도입 뒤: 예정일까지 안 왔으면 데스크가 연락 (오늘 탭)
  const notYet = visit === null || before(due, visit);
  if (after && notYet && canContact(p._owner) && inData(due) && before(due, END)) {
    const day = addDays(due, randInt(0, 2));
    if (!before(END, day) && !onHold(p, day)) {
      const conv = contactFor(p, service, code, due, day);
      if (chance(conv)) visit = addDays(day, randInt(1, Math.max(1, Math.min(12, diffDays(day, addDays(due, MISS_THRESHOLD_DAYS))))));
      else if (visit !== null && !before(addDays(due, MISS_THRESHOLD_DAYS), visit)) visit = chance(0.5) ? addDays(due, randInt(MISS_THRESHOLD_DAYS + 1, 60)) : null;
    }
  } else if (after && !notYet && canContact(p._owner) && inData(due) && chance(CONTACT_THIS_WEEK_SHARE)) {
    // 도입 뒤: 이번 주 탭 — 예정일 전에 미리 연락 (예정일까지 아직 안 온 아이)
    const day = addDays(due, -randInt(1, 5));
    if (inData(day) && before(day, visit) && !onHold(p, day)) {
      const conv = contactFor(p, service, code, due, day);
      if (!chance(conv)) visit = chance(0.6) ? addDays(due, randInt(MISS_THRESHOLD_DAYS + 1, 60)) : null; // 거절 · 다른 병원 등은 안 옴
    }
  } else if (!after && notYet && canContact(p._owner) && chance(0.03) && inData(due)) {
    const day = addDays(due, randInt(0, 3));
    if (!onHold(p, day)) contactFor(p, service, code, due, day); // 도입 전: 연락 기록이 거의 없음
  }
  if (visit && (before(END, visit) || !alive(p, visit))) return null;
  return visit;
}

/** 반복 예정(간격 일정)을 데이터 기간 끝까지 이어 간다 */
function runSeries(p, service, code, firstDue, intervalFn, missBase = 1) {
  let due = firstDue;
  let guard = 0;
  while (!before(END, due) && guard++ < 400) {
    const v = outcome(p, service, code, due, missBase);
    if (v === null) { // 안 옴: 다음 예정은 1주기 뒤로 넘어가지 않고 그대로 열려 있다. 이후 늦게라도 올 수 있게 한 번 더 시도
      const late = addDays(due, randInt(60, 200));
      if (!before(END, late) && alive(p, late) && chance(0.35)) {
        if (service === 'VACCINE') addVaccination(p, code, late); else addGrooming(p, late);
        due = addDays(late, intervalFn());
        continue;
      }
      break;
    }
    if (inData(v) || before(v, START)) {
      if (service === 'VACCINE') addVaccination(p, code, v); else addGrooming(p, v);
    }
    due = addDays(v, intervalFn());
  }
}

for (const p of pets) {
  const birth = p.birth_date;
  const regFrom = maxDate(p._reg, START);
  const baselineFrom = addDays(START, -330);
  const baselineTo = addDays(START, -15);
  const adultAtStart = before(addDays(birth, 365), START);

  // 종합백신 (주요 접종). 기초 5회 2~3주 간격, 그 뒤 매년 (접종 규칙 표 1장 국내 관행). 방문 날짜는 14~21일 사이로 흔들린다
  if (adultAtStart) {
    const base = randDate(maxDate(baselineFrom, p._reg), baselineTo);
    if (before(base, START)) addVaccination(p, 'DHPPL', base); // 기간 직전 기준 접종 1건 (계산 시작점)
    runSeries(p, 'VACCINE', 'DHPPL', addDays(base, 365), () => 365);
  } else {
    let d = addDays(birth, randInt(42, 56));
    let n = 0;
    while (n < 5 && !before(END, d) && alive(p, d)) {
      if (!before(d, regFrom)) addVaccination(p, 'DHPPL', d);
      n++;
      if (n < 5 && chance(0.06)) d = addDays(d, randInt(30, 60)); // 기초접종이 밀린 아이 (K24)
      else d = addDays(d, randInt(14, 21));
    }
    if (n === 5) runSeries(p, 'VACCINE', 'DHPPL', addDays(d, 365 - randInt(14, 21)), () => 365);
  }

  // 광견병 (주요 접종). 생후 3개월 이상 1회, 그 뒤 매년
  if (adultAtStart) {
    const base = randDate(maxDate(baselineFrom, p._reg), baselineTo);
    if (before(base, START)) addVaccination(p, 'RABIES', base);
    runSeries(p, 'VACCINE', 'RABIES', addDays(base, 365), () => 365);
  } else {
    const first = chance(0.05) ? addDays(birth, randInt(60, 85)) : addDays(birth, randInt(90, 120)); // 일부는 3개월 전에 맞음
    if (!before(END, first) && alive(p, first)) {
      addVaccination(p, 'RABIES', first);
      runSeries(p, 'VACCINE', 'RABIES', addDays(first, 365), () => 365);
    }
  }

  // 선택 접종: 어린 강아지 일부만 기초 2회 (코로나 장염 · 켄넬코프 · 인플루엔자)
  if (!adultAtStart) {
    for (const [code, startAge] of [['CORONA', 42], ['KENNEL', 42], ['INFLUENZA', 56]]) {
      if (!chance(0.5)) continue;
      let d = addDays(birth, startAge + randInt(0, 14));
      for (let k = 0; k < 2 && !before(END, d) && alive(p, d); k++) {
        addVaccination(p, code, d);
        d = addDays(d, randInt(14, code === 'INFLUENZA' ? 28 : 21));
      }
    }
  } else {
    for (const code of ['CORONA', 'KENNEL', 'INFLUENZA']) {
      if (!chance(0.15)) continue;
      const base = randDate(maxDate(baselineFrom, p._reg), baselineTo);
      if (before(base, START)) addVaccination(p, code, base);
      runSeries(p, 'VACCINE', code, addDays(base, 365), () => 365, 1.2);
    }
  }

  // 심장사상충 예방약: 병원에서 받는 아이 약 55%, 매월
  if (chance(0.55)) {
    const first = adultAtStart ? randDate(regFrom, addDays(regFrom, 40)) : addDays(birth, randInt(56, 70));
    if (!before(END, first) && alive(p, first) && !before(first, START)) {
      addVaccination(p, 'HEARTWORM', first);
      runSeries(p, 'VACCINE', 'HEARTWORM', addDays(first, 30), () => 30, 1.0);
    }
  }

  // 구충제: 약 35%. K23: 생후 8주까지 14일, 6개월까지 30일, 그 뒤 90일
  if (chance(0.35)) {
    if (adultAtStart) {
      const first = randDate(regFrom, addDays(regFrom, 60));
      if (!before(END, first) && alive(p, first)) {
        addVaccination(p, 'DEWORM', first);
        runSeries(p, 'VACCINE', 'DEWORM', addDays(first, 90), () => 90, 0.9);
      }
    } else {
      let d = addDays(birth, randInt(14, 18));
      while (!before(END, d) && alive(p, d) && diffDays(birth, d) < 182) {
        if (!before(d, regFrom)) addVaccination(p, 'DEWORM', d);
        d = addDays(d, diffDays(birth, d) < 56 ? 14 : 30);
      }
      if (diffDays(birth, d) >= 182) runSeries(p, 'VACCINE', 'DEWORM', d, () => 90, 0.9);
    }
  }

  // 미용
  if (p._groom) {
    const base = COAT_DEFAULT_DAYS[p.coat_type_code];
    const first = randDate(regFrom, addDays(regFrom, base));
    if (before(END, first) || !alive(p, first)) continue;
    if (p._groom === 'FEW') { if (chance(0.5)) addGrooming(p, first); continue; } // 기록 0~1회
    addGrooming(p, first);
    let bigGapDone = false;
    const interval = () => {
      if (p._groom === 'REGULAR') return base + randInt(-3, 3);
      if (p._groom === 'JUMPY') {
        if (!bigGapDone && chance(0.25)) { bigGapDone = true; return Math.round(base * (2.2 + rnd())); } // 한 번 크게 늦음 (다른 곳에서 미용)
        return base + randInt(-5, 7);
      }
      return base + randInt(-8, 14);
    };
    runSeries(p, 'GROOMING', null, addDays(first, interval()), interval, 1.0);
    // 미용 기록 3~10회 범위로 자른다 (매뉴얼 2장)
    const mine = groomings.filter((g) => g.pet_id === p.pet_id);
    if (mine.length > 10) {
      const keep = new Set(mine.slice(-10));
      for (let i = groomings.length - 1; i >= 0; i--) {
        if (groomings[i].pet_id === p.pet_id && !keep.has(groomings[i])) groomings.splice(i, 1);
      }
    }
  }
}

// 다두 가구: 한 번 연락으로 같은 집 아이가 함께 온 기록 (가설 2) — 형제 아이의 같은 날 미용을 추가
for (const o of owners.filter((x) => multiOwners.has(x.guardian_id))) {
  const kids = pets.filter((p) => p.guardian_id === o.guardian_id);
  const g0 = groomings.filter((g) => g.pet_id === kids[0].pet_id && !before(g.visit_date, CRM_START));
  if (g0.length && kids[1] && kids[1]._groom && chance(0.5)) {
    const d = pick(g0).visit_date;
    if (alive(kids[1], d) && !groomings.some((g) => g.pet_id === kids[1].pet_id && g.visit_date === d)) addGrooming(kids[1], d);
  }
}

// 다두 가구 묶음 연락 (매뉴얼 3-1 · 3-3, FR-TGT-002, 가설 2): 도입 뒤 미용 전화 예약(BOOKED) 한 번에
// 같은 집 다른 아이도 함께 묶고, 두 아이가 같은 날 미용을 받은 기록을 만든다.
// 묶음 행은 같은 contact_group_id · 보호자 · 연락 시각 · 방법 · 결과를 쓴다 (DB 설계 contact_log 묶음).
// 앞 단계의 무작위 순서를 바꾸지 않도록 마지막에 덧붙인다.
const GROUP_CONTACT_SHARE = 0.6;
const GROOM_CAP = 10; // 매뉴얼 2장: 아이마다 미용 10회 이하
for (const o of owners.filter((x) => multiOwners.has(x.guardian_id) && canContact(x))) {
  const kids = pets.filter((p) => p.guardian_id === o.guardian_id);
  const booked = contacts.filter((c) => c.guardian_id === o.guardian_id && c.service_type === 'GROOMING' && c.method === 'PHONE'
    && c.result === 'BOOKED' && !before(c.contacted_at.slice(0, 10), CRM_START));
  if (!booked.length || !chance(GROUP_CONTACT_SHARE)) continue;
  const c = pick(booked);
  const day = c.contacted_at.slice(0, 10);
  const a = kids.find((k) => k.pet_id === c.pet_id);
  // 예약으로 온 날: 연락 뒤 기준(MISS_THRESHOLD_DAYS) 안의 첫 미용
  const visit = groomings.filter((g) => g.pet_id === a.pet_id && before(day, g.visit_date)
    && !before(addDays(day, MISS_THRESHOLD_DAYS), g.visit_date)).map((g) => g.visit_date).sort()[0];
  if (!visit) continue;
  const b = kids.find((k) => k !== a && k._groom && alive(k, visit) && !onHold(k, day) && !before(day, k.birth_date)
    && groomings.filter((g) => g.pet_id === k.pet_id).length < GROOM_CAP
    && !contacts.some((x) => x.contact_group_id === c.contact_group_id && x.pet_id === k.pet_id));
  if (!b) continue;
  contacts.push({ ...c, pet_id: b.pet_id });
  if (!groomings.some((g) => g.pet_id === b.pet_id && g.visit_date === visit)) addGrooming(b, visit);
}

// 무지개다리 뒤 · 생년월일 전 기록은 지운다 (매뉴얼 4장)
const petById = new Map(pets.map((p) => [p.pet_id, p]));
const okDate = (r) => {
  const p = petById.get(r.pet_id);
  return !before(r.visit_date ?? r.contacted_at.slice(0, 10), p.birth_date) && alive(p, (r.visit_date ?? r.contacted_at.slice(0, 10)));
};
const vac = vaccinations.filter(okDate).sort((a, b) => a.pet_id - b.pet_id || a.visit_date.localeCompare(b.visit_date));
const gro = groomings.filter(okDate).sort((a, b) => a.pet_id - b.pet_id || a.visit_date.localeCompare(b.visit_date));
const con = contacts.filter(okDate).sort((a, b) => a.contacted_at.localeCompare(b.contacted_at));

// ---------------------------------------------------------------------
// CSV 쓰기
// ---------------------------------------------------------------------
function csv(rows, cols) {
  const esc = (v) => {
    const s = v === null || v === undefined ? '' : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  return [cols.join(','), ...rows.map((r) => cols.map((c) => esc(r[c])).join(','))].join('\n') + '\n';
}
mkdirSync(OUT_DIR, { recursive: true });
const files = {
  'staff.csv': [staff, ['login_id', 'name', 'role', 'is_active']],
  'owners.csv': [owners, ['guardian_id', 'name', 'phone', 'sub_phone', 'preferred_contact', 'phone_status', 'phone_checked_at',
    'privacy_agreed', 'privacy_agreed_at', 'sms_agreed', 'sms_agreed_at', 'tag_no_contact', 'tag_sms_only', 'tag_sensitive',
    'tag_contact_time', 'contact_time_note', 'created_by_login_id', 'created_at']],
  'pets.csv': [pets, ['pet_id', 'guardian_id', 'name', 'species', 'breed_name', 'coat_type_code', 'birth_date', 'birth_date_estimated',
    'registration_no', 'has_pet_insurance', 'weight_kg', 'status', 'rainbow_at', 'contact_hold_until', 'contact_hold_by_pet_id',
    'created_by_login_id', 'created_at']],
  'vaccinations.csv': [vac, ['pet_id', 'vaccine_type_code', 'visit_date', 'memo', 'brought_by_phone', 'created_by_login_id', 'created_at']],
  'groomings.csv': [gro, ['pet_id', 'visit_date', 'memo', 'brought_by_phone', 'created_by_login_id', 'created_at']],
  'contacts.csv': [con, ['contact_group_id', 'guardian_id', 'pet_id', 'service_type', 'vaccine_type_code', 'method', 'result',
    'is_mock_sms', 'sms_template', 'sms_body', 'talked_with', 'memo', 'next_contact_date', 'contacted_at', 'created_by_login_id']],
};
for (const [name, [rows, cols]] of Object.entries(files)) {
  writeFileSync(resolve(OUT_DIR, name), csv(rows, cols), 'utf8');
}
console.log(`owners ${owners.length}, pets ${pets.length}, vaccinations ${vac.length}, groomings ${gro.length}, contacts ${con.length}, staff ${staff.length}`);
console.log('breeds used:', [...new Set(pets.map((p) => p.breed_name))].join(', '));
