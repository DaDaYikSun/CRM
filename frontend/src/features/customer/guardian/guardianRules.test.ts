import { describe, expect, it } from "vitest";
import {
  buildGuardianPatch,
  formatPhone,
  guardianFormErrors,
  holdLabel,
  parseWeight,
  revertConfirmMessage,
  toggleTag,
  type GuardianFormState,
} from "./guardianRules";

const saved: GuardianFormState = {
  name: "김보호",
  phone: "01000000000",
  subPhone: "",
  preferredContact: "PHONE",
  tags: ["SENSITIVE"],
  contactTimeNote: "",
};

describe("S-06 보호자 수정", () => {
  it("바뀐 것이 없으면 요청을 보내지 않는다", () => {
    expect(buildGuardianPatch(saved, { ...saved, phone: "010-0000-0000" })).toBeNull();
  });

  it("바뀐 칸만 담고 번호는 숫자만 보낸다", () => {
    expect(buildGuardianPatch(saved, { ...saved, phone: "010-1111-2222", preferredContact: "" })).toEqual({
      phone: "01011112222",
      preferredContact: null,
    });
  });

  it("태그를 붙이고 메모를 넣으면 둘 다 담긴다", () => {
    const tags = toggleTag(saved.tags, "CONTACT_TIME");
    expect(tags).toEqual(["SENSITIVE", "CONTACT_TIME"]);
    expect(buildGuardianPatch(saved, { ...saved, tags, contactTimeNote: "오후 2시 이후" })).toEqual({
      tags,
      contactTimeNote: "오후 2시 이후",
    });
  });

  it("태그를 떼면 4가지 순서를 지키고 빈 이름 · 잘못된 번호는 오류", () => {
    expect(toggleTag(["NO_CONTACT", "SENSITIVE"], "NO_CONTACT")).toEqual(["SENSITIVE"]);
    const errors = guardianFormErrors({ ...saved, name: " ", phone: "123" });
    expect(errors.name).toBe("보호자 이름을(를) 입력해 주세요.");
    expect(errors.phone).toBeDefined();
  });
});

describe("문구 · 체중", () => {
  it("정책 4-2 문구 그대로", () => {
    expect(revertConfirmMessage("보리")).toBe(
      "보리의 무지개다리 표시를 되돌리면 연락 대상과 통계에 다시 들어가고, 이 아이 때문에 생긴 같은 집 연락 보류가 풀립니다.",
    );
    expect(holdLabel("2026-12-24")).toBe("2026-12-24까지 연락 보류");
  });

  it("체중은 소수 한 자리 · 0 초과 · 999.9 이하", () => {
    expect(parseWeight("")).toBeUndefined();
    expect(parseWeight("3.4")).toBe(3.4);
    expect(parseWeight("0")).toBeNull();
    expect(parseWeight("3.45")).toBeNull();
    expect(parseWeight("1000")).toBeNull();
  });

  it("전체 번호 표시", () => {
    expect(formatPhone("01000000000")).toBe("010-0000-0000");
  });
});
