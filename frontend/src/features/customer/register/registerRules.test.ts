import { describe, expect, it } from "vitest";
import {
  EMPTY_FORM,
  REGISTERED_TOAST,
  REGISTER_COPY,
  REQUIRED_LABELS,
  birthInvalid,
  canSave,
  defaultCoatTypeFor,
  duplicateWarning,
  fieldFromServer,
  missingFields,
  requiredMessage,
  shouldCheckPhone,
  toPetInput,
  toRegisterInput,
  type RegisterFormState,
  type RegisterMode,
  type RequiredField,
} from "./registerRules";

const NEW: RegisterMode = { kind: "new" };
const EXISTING: RegisterMode = { kind: "existing", guardianId: 7, guardianName: "김보호", petCount: 2 };

const FILLED: RegisterFormState = {
  ...EMPTY_FORM,
  phone: "010-0000-0000",
  guardianName: "김보호",
  petName: "보리",
  birthDate: "2023-05-02",
  breedId: "1",
  coatTypeId: "3",
};

const CLEARED: Record<RequiredField, Partial<RegisterFormState>> = {
  phone: { phone: " - " },
  guardianName: { guardianName: "  " },
  petName: { petName: "" },
  birth: { birthDate: "" },
  breedId: { breedId: "" },
  coatTypeId: { coatTypeId: "" },
};

describe("필수 6칸 (POL-REG-001, T9)", () => {
  it("6칸이 다 차면 저장할 수 있다", () => {
    expect(missingFields(FILLED, NEW)).toEqual([]);
    expect(canSave(FILLED, NEW)).toBe(true);
  });

  it.each(Object.keys(CLEARED) as RequiredField[])("%s 칸이 비면 저장 버튼이 꺼진다", (field) => {
    const form = { ...FILLED, ...CLEARED[field] };
    expect(missingFields(form, NEW)).toEqual([field]);
    expect(canSave(form, NEW)).toBe(false);
  });

  it("빈 폼은 6칸 모두 빠짐", () => {
    expect(missingFields(EMPTY_FORM, NEW)).toHaveLength(6);
  });

  it("칸 이름은 POL-REG-001 그대로, 빠진 칸 문구는 정책 4-2", () => {
    expect(Object.values(REQUIRED_LABELS)).toEqual([
      "보호자 전화번호",
      "보호자 이름",
      "아이 이름",
      "생년월일",
      "품종",
      "털 유형",
    ]);
    expect(requiredMessage("보호자 이름")).toBe("보호자 이름을(를) 입력해 주세요.");
  });

  it("생년월일 모름이면 대략 나이 숫자가 있어야 한다", () => {
    const unknown = { ...FILLED, birthDate: "", birthUnknown: true };
    expect(missingFields(unknown, NEW)).toEqual(["birth"]);
    expect(canSave({ ...unknown, approxValue: "3" }, NEW)).toBe(true);
    expect(canSave({ ...unknown, approxValue: "1.5" }, NEW)).toBe(false);
  });

  it("미래 생년월일 · 범위 밖 대략 나이는 저장할 수 없다 (POL-REG-004)", () => {
    expect(birthInvalid({ ...FILLED, birthDate: "2026-11-27" }, "2026-11-26")).toBe(true);
    expect(birthInvalid({ ...FILLED, birthDate: "2026-11-26" }, "2026-11-26")).toBe(false);
    const unknown = { ...FILLED, birthDate: "", birthUnknown: true };
    const bad = (approxValue: string, approxUnit: "months" | "years") =>
      !canSave({ ...unknown, approxValue, approxUnit }, NEW);
    expect(bad("-3", "years")).toBe(true);
    expect(bad("0", "years")).toBe(true);
    expect(bad("0", "months")).toBe(true);
    expect(bad("12", "months")).toBe(true);
    expect(bad("31", "years")).toBe(true);
    expect(bad("1", "months")).toBe(false);
    expect(bad("11", "months")).toBe(false);
    expect(bad("1", "years")).toBe(false);
    expect(bad("30", "years")).toBe(false);
  });

  it("기존 보호자에 아이를 붙일 때는 보호자 칸을 보지 않는다", () => {
    const form = { ...FILLED, guardianName: "" };
    expect(canSave(form, EXISTING)).toBe(true);
    expect(missingFields({ ...form, petName: "" }, EXISTING)).toEqual(["petName"]);
  });
});

describe("보내는 값 (API 설계 3-3)", () => {
  it("전화번호는 숫자만, 동의는 체크한 그대로", () => {
    const input = toRegisterInput({ ...FILLED, phone: "010 0000-0000", smsAgreed: true });
    expect(input).toEqual({
      guardian: { name: "김보호", phone: "01000000000", privacyAgreed: false, smsAgreed: true },
      pet: { name: "보리", birthDate: "2023-05-02", approxAge: null, breedId: 1, coatTypeId: 3 },
    });
  });

  it("대략 나이 3개월 · 5살은 approxAge로만 보낸다 (POL-REG-004)", () => {
    const months = toPetInput({ ...FILLED, birthUnknown: true, approxValue: "3", approxUnit: "months" });
    expect(months.birthDate).toBeNull();
    expect(months.approxAge).toEqual({ months: 3 });
    const years = toPetInput({ ...FILLED, birthUnknown: true, approxValue: "5", approxUnit: "years" });
    expect(years.approxAge).toEqual({ years: 5 });
  });

  it("생년월일을 알면 approxAge는 null", () => {
    expect(toPetInput({ ...FILLED, approxValue: "5" }).approxAge).toBeNull();
  });
});

describe("같은 번호 (POL-REG-005, 정책 4-2)", () => {
  it("경고 문구 그대로", () => {
    expect(duplicateWarning("김보호", 2)).toBe("이미 등록된 보호자입니다: 김보호 (아이 2마리)");
  });

  it("번호를 다 넣었을 때만 확인한다", () => {
    expect(shouldCheckPhone("010-0000-0000")).toBe(true);
    expect(shouldCheckPhone("02-123-4567")).toBe(true);
    expect(shouldCheckPhone("010-0000")).toBe(false);
  });
});

describe("품종을 고르면 기본 털 유형 (DB 4-4, API 설계 3-2)", () => {
  const breeds = [
    { breedId: 1, name: "말티즈", defaultCoatTypeId: 3 },
    { breedId: 2, name: "푸들", defaultCoatTypeId: null },
  ];
  it("기본 털 유형이 있으면 그 값", () => {
    expect(defaultCoatTypeFor(breeds, "1")).toBe("3");
  });
  it("기본 털 유형이 없으면 미리 고르지 않는다", () => {
    expect(defaultCoatTypeFor(breeds, "2")).toBeNull();
  });
});

describe("서버 오류 칸 (API 설계 1-2)", () => {
  it("fields의 칸 이름을 화면 칸으로", () => {
    expect(fieldFromServer("guardian.name")).toBe("guardianName");
    expect(fieldFromServer("pet.approxAge")).toBe("birth");
    expect(fieldFromServer("pet.breedId")).toBe("breedId");
    expect(fieldFromServer("page")).toBeNull();
  });
});

describe("정책 4-4 S-08 문구 다섯 줄 · 4-2 등록 안내 (글자 그대로)", () => {
  it("문구", () => {
    expect(REGISTER_COPY.intro).toBe(
      "필수 6칸만 채우면 저장할 수 있습니다. 나머지는 나중에 채웁니다. 보호자 정보는 보호자 상세에서, 아이 정보는 아이 상세에서 고칩니다.",
    );
    expect(REGISTER_COPY.approxAge).toBe(
      "1살 미만은 개월 수, 그 이상은 나이로 받습니다. 오늘에서 뺀 날을 생년월일로 쓰고 '추정' 표시를 붙입니다.",
    );
    expect(REGISTER_COPY.options).toBe("품종·털 유형은 정해진 선택지에서만 고릅니다. 목록은 백오피스에서 관리합니다.");
    expect(REGISTER_COPY.smsConsent).toBe("동의하지 않으면 문자 보내기 버튼이 꺼집니다. 전화 연락은 할 수 있습니다.");
    expect(REGISTER_COPY.afterSave).toBe("첫 접종·미용을 기록하면 예정일이 다시 계산됩니다.");
    expect(REGISTERED_TOAST).toBe("보호자와 아이를 등록했습니다.");
  });
});
