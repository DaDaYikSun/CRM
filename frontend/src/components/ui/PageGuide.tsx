import { PAGE_GUIDE, type GuideScreen } from "./pageGuides";
import styles from "./PageGuide.module.css";

// 화면마다 제목 아래 한 줄 안내 (FR-COM-002, #168). 문구는 정책 4-4 '제목 아래' 줄 그대로 (pageGuides.ts).
// <h1> 바로 아래에 둔다: <PageGuide screen="S-02" />
export function PageGuide({ screen }: { screen: GuideScreen }) {
  return <p className={styles.guide}>{PAGE_GUIDE[screen]}</p>;
}
