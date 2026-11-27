import Link from "next/link";
import { Badge } from "@/components/ui/Badge";
import type { SearchItem } from "@/lib/api/search";
import { ageLabel } from "./age";
import styles from "./search.module.css";

// 결과 한 줄 (SCR-S05-02). 배지는 정책 4-2에서 S-05가 쓰는 것 중 생년월일 추정만 (무지개다리 배지 · 회색은 초안 21).
export function SearchResultRow({ item, compact = false }: { item: SearchItem; compact?: boolean }) {
  return (
    <Link href={`/pets/${item.petId}`} className={styles.row}>
      <span className={styles.name}>{item.petName}</span>
      <span className={styles.meta}>
        {item.guardianName} · {item.phoneLast4} · {item.breedName} · {ageLabel(item.birthDate)}
      </span>
      {item.birthDateEstimated && <Badge>생년월일 추정</Badge>}
      {!compact && item.siblingNames.length > 0 && (
        <span className={styles.sibling}>같은 집 {item.siblingNames.join(", ")}</span>
      )}
    </Link>
  );
}
