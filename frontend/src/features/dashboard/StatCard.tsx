import Link from "next/link";
import styles from "./dashboard.module.css";

// CMP-U06 StatCard (컴포넌트 설계 3-2): 대시보드 숫자 카드. 누르면 그 탭으로 간다 (SCR-S02-07).
// 로딩이면 숫자 자리에 스켈레톤을 그린다 (SCR-S02-03). 로딩 글자는 페이지가 한 번만 읽어 준다.
export type StatCardProps =
  | { label: string; loading: true }
  | { label: string; value: string; href: string; linkLabel: string; loading?: false };

export function StatCard(props: StatCardProps) {
  const { label } = props;
  if (props.loading) {
    return (
      <div className={styles.card} aria-hidden="true">
        <span className={styles.label}>{label}</span>
        <span className={styles.skeleton} />
      </div>
    );
  }
  return (
    <Link href={props.href} className={`${styles.card} ${styles.link}`}>
      <span className={styles.label}>{label}</span>
      <strong className={styles.value}>{props.value}</strong>
      <span className={styles.more}>{props.linkLabel}</span>
    </Link>
  );
}
