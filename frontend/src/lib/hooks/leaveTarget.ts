// 나가기 경고가 막을 링크인가 (#184, useLeaveConfirm). 화면 밖에서 테스트할 수 있게 DOM 없이 판단한다.
export interface LinkClick {
  href: string | null;
  target: string | null;
  download: boolean;
  button: number;
  modified: boolean; // ctrl · meta · shift · alt
}

/**
 * 이 클릭이 지금 탭에서 이 사이트의 다른 화면으로 옮기는가. 옮기면 그 주소(경로 + 검색어 + 해시), 아니면 null.
 * 새 탭 · 다운로드 · 다른 사이트 · 같은 화면 안 해시 이동은 막지 않는다.
 */
export function leaveTarget(click: LinkClick, current: string, origin: string): string | null {
  if (!click.href || click.button !== 0 || click.modified || click.download) return null;
  if (click.target && click.target !== "_self") return null;
  let url: URL;
  let here: URL;
  try {
    url = new URL(click.href, origin + current);
    here = new URL(current, origin);
  } catch {
    return null;
  }
  if (url.origin !== origin) return null;
  if (url.pathname === here.pathname && url.search === here.search) return null;
  return `${url.pathname}${url.search}${url.hash}`;
}
