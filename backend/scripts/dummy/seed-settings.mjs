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
