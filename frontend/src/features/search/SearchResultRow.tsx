import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import type { SearchItem } from "@/lib/api/search";
import { ageLabel } from "./age";
import styles from "./search.module.css";

// 결과 한 줄 (SCR-S05-02). 무지개다리 아이는 나이 대신 '무지개다리' 배지만 보인다 (회색 처리 · 정리는 초안 21).
export function SearchResultRow({ item, compact = false }: { item: SearchItem; compact?: boolean }) {
  const rainbow = item.status === "RAINBOW";
  return (
    <Link href={`/pets/${item.petId}`} className={styles.row}>
      <span className={styles.name}>{item.petName}</span>
      <span className={styles.meta}>
        {item.guardianName} · {item.phoneLast4} · {item.breedName}
        {!rainbow && ` · ${ageLabel(item.birthDate)}`}
      </span>
      {rainbow && <Badge>무지개다리</Badge>}
      {item.birthDateEstimated && !rainbow && <Badge>생년월일 추정</Badge>}
      {!compact && item.siblingNames.length > 0 && (
        <span className={styles.sibling}>같은 집 {item.siblingNames.join(", ")}</span>
      )}
    </Link>
  );
}
