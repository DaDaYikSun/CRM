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
import { formatPhone, seedCoatGroomDays, seedInt, seedSetting, seedVaccineRules } from './seed-settings.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(HERE, '../../src/main/resources/db/dummy');

// ---------------------------------------------------------------------
// 설정
// ---------------------------------------------------------------------
// 씨앗: 매뉴얼 목표(누락률 3-4 · 결과별 전환 3-3 · 연락 700~1,300건)를 모두 만족하는 값으로 골랐다 (PR #238 리뷰). DUMMY_SEED로 바꿔 볼 수 있다
const SEED = Number(process.env.DUMMY_SEED ?? 20261148);
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

// 누락 경향 (매뉴얼 3-2 · 3-4): 도입 전 30% 안팎, 도입 뒤 15~20% (아이 기준). 주기마다 확률은 아래 CYCLE_MISS. 늦었지만 14일 안 15%
const LATE_WITHIN_RATE = 0.15;
// 도입 뒤 예정일까지 안 온 아이에게 연락하는 비율 (미용은 보호자가 알아서 오는 경우가 많아 낮게)
const CONTACT_LATE_SHARE = { VACCINE: 1.0, GROOMING: 0.4 };
const CONTACT_THIS_WEEK_SHARE = 0.12; // 도입 뒤 제때 올 아이 중 예정일 전에 미리 연락한 비율 (이번 주 탭)
const REGULAR_GROOMER_MISS_FACTOR = 0.55; // 미용 꼬박꼬박 오는 아이는 접종 누락이 낮다 (가설 3)

// 연락 결과 비율과 전환 목표 (매뉴얼 3-3)
const RESULT_WEIGHTS = [
  ['BOOKED', 35], ['NO_ANSWER', 30], ['REFUSED', 15], ['OTHER_HOSPITAL', 10], ['WRONG_NUMBER', 10],
];
const CONVERT_RATE = {
  BOOKED: 0.76, REFUSED: 0.06, OTHER_HOSPITAL: 0.03, WRONG_NUMBER: 0,
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
// 마지막 연락 결과 (2일차까지 본 결과). 거절 · 다른 병원 · 번호 오류면 연락이 재방문으로 이어지지 않을 때 오지 않는다
let lastContactResult = null;
const NOT_COMING = ['REFUSED', 'OTHER_HOSPITAL', 'WRONG_NUMBER'];
function contactFor(p, service, code, due, day) {
  lastContactResult = null;
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
      cycleContacts.push(contacts.length);
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
    lastContactResult = 'NO_ANSWER';
    if (before(END, d2) || onHold(p, d2)) return 0;
    const follow = o.sms_agreed && chance(0.5) ? 'SMS' : 'PHONE';
    const groupId2 = uuid();
    const r2 = follow === 'PHONE' ? (chance(0.6) ? 'NO_ANSWER' : 'BOOKED') : null;
    for (const it of items) {
      cycleContacts.push(contacts.length);
      contacts.push({
        contact_group_id: groupId2, guardian_id: o.guardian_id, pet_id: it.p.pet_id, service_type: service,
        vaccine_type_code: it.code ?? '', method: follow, result: r2 ?? '', is_mock_sms: follow === 'SMS' ? 1 : 0,
        sms_template: follow === 'SMS' ? service : '', sms_body: follow === 'SMS' ? smsBody(service, it.p, due, it.code) : '',
        talked_with: r2 === 'BOOKED' ? 'SELF' : '', memo: '2일차', next_contact_date: '',
        contacted_at: workTime(d2), created_by_login_id: desk,
      });
    }
    lastContactResult = r2 ?? 'NO_ANSWER';
    if (r2 === 'BOOKED') return CONVERT_RATE.BOOKED;
    return follow === 'SMS' ? CONVERT_RATE.NO_ANSWER_SMS : CONVERT_RATE.NO_ANSWER_PHONE;
  }
  rows(method, result, day, result === 'REFUSED' ? pick(['다음에 연락 달라고 함', '당분간 안 온다고 함', '']) : '');
  lastContactResult = result;
  if (result === null) return CONVERT_RATE.NO_ANSWER_SMS; // 문자만 보내고 답 없음
  return CONVERT_RATE[result];
}

// ---------------------------------------------------------------------
// 예정일 계산: 백엔드 cycle과 같은 규칙 (DB 설계 4-7 · 5-1, K24, PR #202)
// 적재기는 방문을 시간 순서로 넣으며 cycle을 부른다. 여기서도 같은 규칙으로 예정일을 내야
// 생성 때 의도한 '제때 · 늦음 · 누락'이 적재 뒤 저장된 missed_at과 같아진다.
// ---------------------------------------------------------------------
const VACCINE_RULES = seedVaccineRules(); // 마이그레이션(V2 · V4 …)의 vaccine_rule 행
const MAJOR = ['DHPPL', 'RABIES']; // 주요 접종 (#59, 누락률에 넣음)

/** VaccineDueDateCalculator.nextDueDate + SchedulePlanner.nextVaccineDue (기록 없으면 등록일보다 이르지 않게) */
function nextVaccineDue(p, code, doses) {
  const rules = VACCINE_RULES[code];
  if (!rules) return null;
  if (!doses.length) return maxDate(addDays(p.birth_date, rules[0].from), p._reg);
  const last = doses[doses.length - 1];
  let remaining = doses.length;
  for (let i = 0; i < rules.length; i++) {
    const row = rules[i];
    if (row.count === null) continue;
    if (remaining < row.count) return addDays(last, row.interval);
    if (remaining === row.count) {
      const nextCounted = rules.slice(i + 1).find((r) => r.count !== null);
      if (nextCounted) return addDays(last, nextCounted.interval);
      if (i + 1 < rules.length) return addDays(last, rules[i + 1].interval);
      break;
    }
    remaining -= row.count;
  }
  const age = diffDays(p.birth_date, last);
  const repeating = rules.filter((r) => r.count === null);
  if (!repeating.length) return null;
  let chosen = repeating[0];
  for (const r of repeating) {
    if (r.from <= age && (r.to === null || age < r.to)) return addDays(last, r.interval);
    if (r.from <= age) chosen = r;
  }
  return addDays(last, chosen.interval);
}

/** GroomIntervalCalculator + SchedulePlanner.nextGroom: 최근 5건 간격 중 기본 간격 2배 이하의 중앙값, 2회 미만이면 기본 간격 */
function nextGroomDue(p, dates) {
  const def = COAT_DEFAULT_DAYS[p.coat_type_code];
  const uniq = [...new Set(dates)].sort();
  let days = def;
  if (uniq.length >= 2) {
    const recent = uniq.slice(-5);
    const gaps = [];
    for (let i = 1; i < recent.length; i++) { const g = diffDays(recent[i - 1], recent[i]); if (g <= def * 2) gaps.push(g); }
    if (gaps.length) {
      gaps.sort((a, b) => a - b);
      const n = gaps.length;
      days = n % 2 ? gaps[(n - 1) / 2] : Math.floor((gaps[n / 2 - 1] + gaps[n / 2] + 1) / 2);
    }
  }
  return addDays(uniq.length ? uniq[uniq.length - 1] : p._reg, days);
}

// 주기마다 누락 확률. 지표는 아이 기준(기간 안 주기 중 하나라도 누락이면 누락, T15)이라
// 주기 하나의 확률은 매뉴얼 목표(도입 전 30% 안팎 · 도입 뒤 15~20%)보다 낮게 둔다. 맞춘 값은 끝에 출력한다.
const CYCLE_MISS = {
  VACCINE: { before: 0.17, after: 0.17 },
  GROOMING: { before: 0.01, after: 0.005 },
};
const JUDGE_DAY = addDays(END, 1); // 적재 뒤 판정 작업이 도는 날 (END 다음 날로 본다)
const BASELINE_FROM = '2024-07-01'; // 등록 뒤 기록은 이날부터 (기간 직전 기준 접종)
const GROOM_CAP = 10; // 매뉴얼 2장: 미용 오는 아이마다 3~10회
let cycleContacts = []; // 지금 주기에서 만든 연락 행 (전환 기대값 계산용)
const cycles = []; // { p, service, code, due, visit } 생성 때 기대 누락률을 세는 데 쓴다

/** 연락 결과로 이번 주기에 기준 안에 오지 않음: 한참 늦게 오거나 안 옴 */
const notComing = (due) => (chance(0.5) ? addDays(due, randInt(MISS_THRESHOLD_DAYS + 1, 60)) : null);

/**
 * 예정 하나(due)에 대한 실제 방문일을 정한다. 안 오면 null.
 * 도입 뒤 예정은 제때 안 왔으면 연락하고, 연락 결과에 따라 기준(14일) 안에 오게 한다.
 */
function outcome(p, service, code, due, missBase) {
  const after = !before(due, CRM_START);
  let missRate = CYCLE_MISS[service][after ? 'after' : 'before'] * missBase;
  if (p._groom === 'REGULAR' && service === 'VACCINE') missRate *= REGULAR_GROOMER_MISS_FACTOR;
  const r = rnd();
  let visit;
  if (r < missRate) visit = chance(0.55) ? addDays(due, randInt(MISS_THRESHOLD_DAYS + 1, 90)) : null; // 누락 (늦게 오거나 안 옴)
  else if (r < missRate + LATE_WITHIN_RATE) visit = addDays(due, randInt(1, MISS_THRESHOLD_DAYS)); // 늦었지만 기준 안
  else visit = addDays(due, randInt(-3, 0)); // 제때

  // 도입 뒤: 예정일까지 안 왔으면 데스크가 연락 (오늘 탭)
  const notYet = visit === null || before(due, visit);
  if (after && notYet && canContact(p._owner) && inData(due) && before(due, END) && chance(CONTACT_LATE_SHARE[service])) {
    const day = addDays(due, randInt(0, 2));
    // 연락은 그 주기의 방문보다 앞서야 한다. 방문 뒤 연락은 다음 주기에 붙으므로 만들지 않는다 (PR #226 CodeRabbit)
    if (!before(END, day) && !onHold(p, day) && (visit === null || before(day, visit))) {
      const conv = contactFor(p, service, code, due, day);
      // 연락이 재방문으로 이어지면 기준 안에 온다. 이어지지 않으면 원래 정해진 대로, 다만 거절 · 다른 병원 · 번호 오류면 기준 안에 오지 않는다
      // (전환 판정 #154는 연락 뒤 기준 안 방문을 전환으로 세므로, 결과별 전환율이 매뉴얼 3-3과 맞게)
      if (chance(conv)) visit = addDays(day, randInt(1, Math.max(1, Math.min(12, diffDays(day, addDays(due, MISS_THRESHOLD_DAYS))))));
      else if (NOT_COMING.includes(lastContactResult)) visit = notComing(due);
    }
  } else if (after && !notYet && canContact(p._owner) && inData(due) && chance(CONTACT_THIS_WEEK_SHARE)) {
    // 도입 뒤: 이번 주 탭 — 예정일 전에 미리 연락 (예정일까지 아직 안 온 아이)
    const day = addDays(due, -randInt(1, 5));
    if (inData(day) && before(day, visit) && !onHold(p, day)) {
      const conv = contactFor(p, service, code, due, day); // 제때 올 아이라 방문은 그대로, 거절 · 다른 병원 · 번호 오류만 안 옴
      if (NOT_COMING.includes(lastContactResult) && !chance(conv)) visit = notComing(due);
    }
  } else if (!after && notYet && canContact(p._owner) && chance(0.03) && inData(due)) {
    const day = addDays(due, randInt(0, 3));
    if (!onHold(p, day)) contactFor(p, service, code, due, day); // 도입 전: 연락 기록이 거의 없음
  }
  if (visit && (before(END, visit) || !alive(p, visit))) return null;
  return visit;
}

/**
 * 한 아이 · 서비스(· 접종 종류)의 주기를 등록일부터 데이터 끝까지 이어 간다. 적재기가 cycle로 만들 주기와 같다.
 * history: 등록 전 과거 기록 (이미 넣은 방문일, 오름차순)
 * opts.missBase: 누락 확률 배수, opts.stop(n): 방문 n번째 뒤 그만 오는지 (미용 0~1회 아이)
 */
function simulate(p, service, code, history, opts = {}) {
  const dates = [...history];
  const nextDue = () => (service === 'GROOMING' ? nextGroomDue(p, dates) : nextVaccineDue(p, code, dates));
  const record = (d) => {
    if (service === 'VACCINE') addVaccination(p, code, d); else addGrooming(p, d);
    dates.push(d);
    // 그 주기의 방문 시각 (적재기는 방문일 + 입력 시각으로 줄을 세운다, DummyTimeline.visit)
    const last = cycles[cycles.length - 1];
    if (last && last.p === p && last.visit === d) last.visitAt = `${d} ${(service === 'VACCINE' ? vaccinations : groomings).at(-1).created_at.slice(11)}`;
  };
  let due = nextDue();
  let visits = 0;
  let guard = 0;
  while (due && !before(END, due) && guard++ < 400) {
    if (!alive(p, due)) break;
    if (opts.stop?.(visits)) { cycles.push({ p, service, code, due, visit: null, contacts: cycleContacts.splice(0) }); break; }
    // 기록 시작일(floor) 전에 예정이 된 주기는 기록을 두지 않고, 시작일 무렵 늦게 온 것으로 닫는다 (그 전 기간의 누락)
    const floor = opts.floor ?? BASELINE_FROM;
    if (before(due, floor)) {
      const v = randDate(floor, minDate(addDays(floor, 30), END));
      if (!alive(p, v)) { cycles.push({ p, service, code, due, visit: null, contacts: cycleContacts.splice(0) }); break; } // 무지개다리 뒤 방문은 없다
      cycles.push({ p, service, code, due, visit: v, contacts: cycleContacts.splice(0) });
      record(v);
      visits++;
      due = nextDue();
      continue;
    }
    cycleContacts = [];
    let v = opts.forceLate?.(visits, due) ? addDays(due, randInt(MISS_THRESHOLD_DAYS + 20, MISS_THRESHOLD_DAYS + 60)) : outcome(p, service, code, due, opts.missBase ?? 1);
    if (v && (before(END, v) || !alive(p, v))) v = null;
    // 제때(예정일 며칠 전) 방문이 기록 시작일 · 등록일 앞으로 가지 않게. 등록일 전 방문은 적재기가 등록 전 과거 기록으로 넣어 주기가 어긋난다
    const lower = maxDate(floor, p._reg);
    if (v && before(v, lower)) v = lower;
    if (v === null) {
      // 안 옴: 주기는 열린 채 판정된다. 한참 뒤 늦게라도 오는 아이도 있다
      const late = addDays(due, randInt(60, 200));
      if (!before(END, late) && alive(p, late) && chance(0.35)) {
        cycles.push({ p, service, code, due, visit: late, contacts: cycleContacts.splice(0) });
        record(late);
        visits++;
        due = nextDue();
        continue;
      }
      cycles.push({ p, service, code, due, visit: null, contacts: cycleContacts.splice(0) });
      break;
    }
    cycles.push({ p, service, code, due, visit: v, contacts: cycleContacts.splice(0) });
    record(v);
    visits++;
    due = nextDue();
  }
}

/** 등록 전 과거 기록 (등록할 때 이전 병원 · 수첩에서 옮겨 적은 접종). 계산이 기초접종을 다 채운 것으로 보게 한다 (K24) */
function priorVaccines(p) {
  const reg = p._reg;
  const out = { DHPPL: [], RABIES: [] };
  const addIf = (code, d) => { if (before(d, reg) && !before(d, p.birth_date)) out[code].push(d); };
  // 종합백신 기초 5회 (6차 접종표 1~5차), 1살 넘었으면 등록 전 1년 안 추가접종 1회
  for (let k = 0; k < 5; k++) addIf('DHPPL', addDays(p.birth_date, 42 + 14 * k + randInt(0, 3)));
  if (out.DHPPL.length === 5 && diffDays(p.birth_date, reg) > 365 + 98) {
    addIf('DHPPL', randDate(maxDate(addDays(reg, -330), addDays(p.birth_date, 365)), addDays(reg, -15)));
  }
  // 광견병 1회 (등록 전 1년 안)
  if (diffDays(p.birth_date, reg) > 112 + 15) {
    addIf('RABIES', randDate(maxDate(addDays(reg, -330), addDays(p.birth_date, 112)), addDays(reg, -15)));
  }
  return out;
}

for (const p of pets) {
  const birth = p.birth_date;

  // 주요 접종 (종합백신 · 광견병): 모든 아이. 등록 전 과거 기록을 먼저 두고 등록일부터 주기를 돈다
  const prior = priorVaccines(p);
  for (const code of MAJOR) {
    for (const d of prior[code]) addVaccination(p, code, d);
    simulate(p, 'VACCINE', code, prior[code]);
  }

  // 심장사상충 · 구충제: 규칙이 있어 모든 아이에게 예정이 생긴다. 병원에서 받는 아이(약 55% · 35%)는 주기대로 오고,
  // 나머지는 첫 예정부터 오지 않는다 (열린 주기가 판정됨, 주요 접종이 아니라 누락률에는 안 들어감)
  for (const [code, share] of [['HEARTWORM', 0.55], ['DEWORM', 0.35]]) {
    const uses = chance(share);
    simulate(p, 'VACCINE', code, [], uses ? { missBase: 1.0, floor: START } : { stop: () => true });
  }

  // 선택 접종 (코로나 장염 · 켄넬코프 · 인플루엔자): 규칙 행은 #204 뒤에 들어와 지금은 예정이 생기지 않는다.
  // 기록만 접종 규칙 표 1-1 · 3장 값(시작 나이, 2회 14일 간격)으로 둔다
  for (const [code, startAge] of [['CORONA', 42], ['KENNEL', 70], ['INFLUENZA', 98]]) {
    if (!chance(p._puppy ? 0.5 : 0.15)) continue;
    let d = p._puppy ? addDays(birth, startAge + randInt(0, 3)) : randDate(maxDate(BASELINE_FROM, p._reg), addDays(END, -30));
    for (let k = 0; k < 2 && !before(END, d) && alive(p, d); k++) {
      if (!before(d, BASELINE_FROM)) addVaccination(p, code, d);
      if (!p._puppy) break;
      d = addDays(d, 14 + randInt(0, 3));
    }
  }

  // 미용: 모든 아이에게 첫 미용 예정(등록일 + 털 유형 기본 간격)이 생긴다. 미용 안 오는 아이는 첫 예정부터 오지 않는다
  if (!p._groom) { simulate(p, 'GROOMING', null, [], { stop: () => true }); continue; }
  if (p._groom === 'FEW') { // 기록 0~1회
    const once = chance(0.5);
    simulate(p, 'GROOMING', null, [], { stop: (n) => n >= (once ? 1 : 0) });
    continue;
  }
  const bigGapAt = p._groom === 'JUMPY' ? randInt(1, 4) : -1; // 한 번 크게 늦음 (다른 곳에서 미용, 매뉴얼 3-2)
  // 미용 기록이 10회를 넘지 않게 미용을 다니기 시작한 날을 정한다 (그 전 예정은 시작일 무렵 늦게 온 것으로 닫힘)
  const def = COAT_DEFAULT_DAYS[p.coat_type_code];
  simulate(p, 'GROOMING', null, [], {
    floor: maxDate(maxDate(BASELINE_FROM, p._reg), addDays(END, -def * 7)),
    missBase: p._groom === 'REGULAR' ? 0.4 : 1.0,
    forceLate: (n) => n === bigGapAt,
  });
}

// 다두 가구 묶음 연락 (매뉴얼 3-1 · 3-3, FR-TGT-002, 가설 2): 도입 뒤 미용 전화 예약(BOOKED) 한 번에
// 같은 집 다른 아이도 함께 묶는다. 묶음 행은 같은 contact_group_id · 보호자 · 연락 시각 · 방법 · 결과를 쓴다.
// 묶는 아이는 그 연락 뒤 기준(MISS_THRESHOLD_DAYS) 안에 이미 미용을 받은 아이만 고른다 (방문을 새로 만들지 않아 주기 계산이 그대로).
const GROUP_CONTACT_SHARE = 0.6;
const siblingContacts = []; // 묶음으로 더한 아이 행 (기대 전환율 계산용)
for (const o of owners.filter((x) => multiOwners.has(x.guardian_id) && canContact(x))) {
  const kids = pets.filter((p) => p.guardian_id === o.guardian_id);
  const booked = contacts.filter((c) => c.guardian_id === o.guardian_id && c.service_type === 'GROOMING' && c.method === 'PHONE'
    && c.result === 'BOOKED' && !before(c.contacted_at.slice(0, 10), CRM_START));
  if (!booked.length || !chance(GROUP_CONTACT_SHARE)) continue;
  const c = pick(booked);
  const day = c.contacted_at.slice(0, 10);
  const b = kids.find((k) => k.pet_id !== c.pet_id && alive(k, day) && !onHold(k, day)
    && groomings.some((g) => g.pet_id === k.pet_id && before(day, g.visit_date) && !before(addDays(day, MISS_THRESHOLD_DAYS), g.visit_date))
    && !contacts.some((x) => x.contact_group_id === c.contact_group_id && x.pet_id === k.pet_id));
  if (!b) continue;
  siblingContacts.push(contacts.length);
  contacts.push({ ...c, pet_id: b.pet_id });
}

// 기대 누락률 (POL-MISS-003 · 004, T15): 서비스별, 기간 안에 예정일이 된 주기 중 결과가 정해진 것만, 아이 기준.
// 적재 뒤 SQL 집계와 대조한다 (#157). 무지개다리 아이 · 주요 접종이 아닌 접종은 뺀다
function expectedMissRate(service, from, to, only = () => true) {
  const byPet = new Map();
  for (const c of cycles) {
    if (c.service !== service || c.p.status === 'RAINBOW' || !only(c.p)) continue;
    if (service === 'VACCINE' && !MAJOR.includes(c.code)) continue;
    if (before(c.due, from) || before(to, c.due)) continue;
    const missed = c.visit ? before(addDays(c.due, MISS_THRESHOLD_DAYS), c.visit) : before(addDays(c.due, MISS_THRESHOLD_DAYS), JUDGE_DAY);
    const decided = c.visit !== null || missed;
    if (!decided) continue;
    byPet.set(c.p.pet_id, (byPet.get(c.p.pet_id) ?? false) || missed);
  }
  const n = byPet.size;
  const m = [...byPet.values()].filter(Boolean).length;
  return { n, m, rate: n ? Math.round((1000 * m) / n) / 10 : 0 };
}
const lastBefore = addDays(CRM_START, -1);
for (const service of ['VACCINE', 'GROOMING']) {
  const pre = expectedMissRate(service, START, lastBefore);
  const post = expectedMissRate(service, CRM_START, END);
  console.log(`기대 누락률 ${service}: 도입 전 ${pre.rate}% (${pre.m}/${pre.n}), 도입 뒤 ${post.rate}% (${post.m}/${post.n})`);
}
// 기대 전환율 (#154 ConversionRule: 기준 안 방문이면 방문 시각 전 마지막 연락이 전환). 도입 뒤 연락만, 결과별.
// 적재기와 같은 기준으로 연락을 주기에 붙인다: 등록일 전 연락은 넣지 않고, 연락한 시각에 열려 있던 주기(그 시각 뒤에 방문한 첫 주기)에 붙인다.
// 2일차 전화도 안 받으면 후속 처리(C14)가 같은 시각에 모의 발송 문자 행을 더하므로 그 행도 넣는다 (PR #238 리뷰)
{
  const regOf = new Map(pets.map((p) => [p.pet_id, p._reg]));
  const streams = new Map();
  const openAfterEnd = [];
  for (const c of cycles) {
    const k = `${c.p.pet_id}|${c.service}|${c.code}`;
    if (!streams.has(k)) streams.set(k, []);
    streams.get(k).push(c);
    c.rows = [];
  }
  const place = (list, from, x) => {
    let i = from;
    while (i < list.length && list[i].visitAt && x.contacted_at >= list[i].visitAt) i++;
    if (i < list.length) list[i].rows.push(x);
    else openAfterEnd.push(x); // 데이터 끝 뒤 예정의 열린 주기에 붙는다 (방문 없음, 전환 아님)
  };
  const inData = (x) => !before(x.contacted_at.slice(0, 10), regOf.get(x.pet_id));
  for (const list of streams.values()) {
    list.forEach((c, i) => (c.contacts ?? []).forEach((idx) => { if (inData(contacts[idx])) place(list, i, contacts[idx]); }));
  }
  for (const idx of siblingContacts) { // 같은 집 묶음 연락으로 더한 아이 행
    const x = contacts[idx];
    const list = streams.get(`${x.pet_id}|GROOMING|null`);
    if (list && inData(x)) place(list, 0, x);
  }
  const stat = {};
  const out = [];
  for (const c of [...cycles, { rows: openAfterEnd, visit: null, visitAt: null, due: END }]) {
    // 적재기가 넣는 순서 (DummyTimeline.merge: 시각 → 연락 묶음 → 아이 번호). 같은 시각이면 뒤에 넣은 행이 contact_log_id가 크다
    const ordered = c.rows.sort((p1, p2) => (p1.contacted_at !== p2.contacted_at ? (p1.contacted_at < p2.contacted_at ? -1 : 1)
      : p1.contact_group_id !== p2.contact_group_id ? (p1.contact_group_id < p2.contact_group_id ? -1 : 1) : p1.pet_id - p2.pet_id));
    // 주기 연락 상태를 FollowUpRule.after와 같게 따라간다 (모의 발송 문자 행은 상태를 바꾸지 않는다).
    // 2일차로 주기 연락을 끝내면 묶음을 다 넣은 뒤 후속 처리(C14)가 모의 발송 문자 행을 더한다 (수신 동의 있을 때)
    const rows = [];
    let state = 'NONE', count = 0, next = null;
    let pendingMock = null;
    ordered.forEach((x, i) => {
      rows.push(x);
      if (x.method === 'PHONE') {
        const day = x.contacted_at.slice(0, 10);
        if (x.result !== 'NO_ANSWER') { state = x.result; count = 0; next = null; }
        else if (state !== 'CLOSED') {
          if (state === 'NO_ANSWER' && next && before(day, next)) { next = x.next_contact_date || null; }
          else if (count + 1 >= 2) { count += 1; state = 'CLOSED'; next = null; if (owners[x.guardian_id - 1].sms_agreed) pendingMock ??= x; }
          else { count += 1; state = 'NO_ANSWER'; next = x.next_contact_date || null; }
        }
      }
      const groupEnds = i + 1 === ordered.length || ordered[i + 1].contact_group_id !== x.contact_group_id;
      if (groupEnds && pendingMock) { rows.push({ ...pendingMock, method: 'SMS', result: '', memo: '(후속 처리 모의 발송)' }); pendingMock = null; }
    });
    const within = c.visit && !before(addDays(c.due, MISS_THRESHOLD_DAYS), c.visit);
    // 같은 시각이면 나중에 넣은 행 (ConversionRule: 연락 시각 → contact_log_id 순, rows는 넣는 순서)
    const conv = within && rows.length ? rows.reduce((a, b) => (a.contacted_at > b.contacted_at ? a : b)) : null;
    for (const x of rows) {
      if (process.env.DUMMY_CONV_OUT) out.push(`${x.contact_group_id}|${x.method}|${x.result}|${x.pet_id}|${x === conv ? 1 : 0}`);
      if (before(x.contacted_at.slice(0, 10), CRM_START)) continue;
      const k = (x.result || '(문자만)') + ' ' + x.method;
      stat[k] ??= [0, 0];
      stat[k][0]++;
      if (x === conv) stat[k][1]++;
    }
  }
  // DUMMY_CONV_OUT=파일 이면 연락 행마다 기대 전환(묶음|방법|결과|아이|0·1)을 써서 적재 뒤 DB와 행 단위로 대조할 수 있다
  if (process.env.DUMMY_CONV_OUT) writeFileSync(process.env.DUMMY_CONV_OUT, out.join(String.fromCharCode(10)));
  console.log('기대 전환율 (도입 뒤): ' + Object.entries(stat).sort().map(([k, [n, m]]) => `${k} ${Math.round((1000 * m) / n) / 10}% (${m}/${n})`).join(', '));
}
// 미용을 다니는 아이만 (미용 안 오는 아이 · 0~1회 아이의 첫 미용 예정 누락을 뺀 값, 참고용)
const groomers = (p) => p._groom && p._groom !== 'FEW';
const gPre = expectedMissRate('GROOMING', START, lastBefore, groomers);
const gPost = expectedMissRate('GROOMING', CRM_START, END, groomers);
console.log(`  (미용 다니는 아이만: 도입 전 ${gPre.rate}% (${gPre.m}/${gPre.n}), 도입 뒤 ${gPost.rate}% (${gPost.m}/${gPost.n}))`);


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
