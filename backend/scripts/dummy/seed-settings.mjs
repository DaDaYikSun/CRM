// 기준값(setting)을 마이그레이션 SQL에서 읽는다 (CLAUDE.md 3번: 정책 숫자는 코드에 박지 않고 설정에서 읽는다).
// 앱은 setting 테이블에서 읽는다. 더미 생성 · 점검 스크립트는 DB 없이 돌므로 같은 값을 V1 · V2 SQL에서 읽는다.
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const MIGRATION = resolve(dirname(fileURLToPath(import.meta.url)), '../../src/main/resources/db/migration');
const SQL = ['V1__init.sql', 'V2__seed_base.sql'].map((f) => readFileSync(resolve(MIGRATION, f), 'utf8')).join('\n');

/** setting_key의 기본값. 없으면 오류 (값을 지어내지 않는다). */
export function seedSetting(key) {
  const m = SQL.match(new RegExp(String.raw`\('${key}',\s*'([^']*)'`));
  if (!m) throw new Error(`기본 데이터에 ${key} 설정이 없다`);
  return m[1];
}

export const seedInt = (key) => Number.parseInt(seedSetting(key), 10);

/** 털 유형별 미용 기본 간격(일) = coat_type.default_groom_weeks × 7 (POL-CYC-002, DB 설계 4-5). */
export function seedCoatGroomDays() {
  const days = {};
  const start = SQL.indexOf('INSERT INTO coat_type');
  const block = start < 0 ? '' : SQL.slice(start, SQL.indexOf(';', start));
  for (const m of block.matchAll(/\('([A-Z]+)',\s*'[^']*',\s*(\d+),\s*\d+\)/g)) {
    days[m[1]] = Number.parseInt(m[2], 10) * 7;
  }
  for (const code of ['SHORT', 'MEDIUM', 'LONG', 'CURLY']) {
    if (!days[code]) throw new Error(`기본 데이터에 털 유형 ${code}의 미용 기본 간격이 없다`);
  }
  return days;
}

/** 병원 전화번호를 문자 문구에 쓰는 모양으로 (숫자만 저장, 예: 0200000000 → 02-0000-0000). */
export function formatPhone(digits) {
  if (digits.startsWith('02')) return digits.replace(/^(02)(\d{3,4})(\d{4})$/, '$1-$2-$3');
  return digits.replace(/^(\d{3})(\d{3,4})(\d{4})$/, '$1-$2-$3');
}
