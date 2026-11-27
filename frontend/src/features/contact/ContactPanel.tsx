"use client";

import { useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Notice";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { TextField } from "@/components/ui/TextField";
import { Toast } from "@/components/ui/Toast";
import { ApiError, LOAD_FAILED_MESSAGE, newIdempotencyKey, SAVE_FAILED_MESSAGE } from "@/lib/api/client";
import {
  getContactHistory,
  getContactPanel,
  saveContact,
  sendMockSms,
  type ContactHistoryItem,
  type ContactMethod,
  type ContactPanel as Panel,
  type ContactResult,
  type TalkedWith,
} from "@/lib/api/contact";
import { todayKst } from "@/lib/utils/date";
import type { PageData } from "@/types/common";
import {
  canSave,
  COPY,
  defaultNextContactDate,
  formatPhone,
  isNightKst,
  isSecondDayNoAnswer,
  MEMO_MAX,
  isSecondDayItem,
  savedToast,
  secondDayCopy,
  todayContactMessage,
  type Results,
} from "./contactRules";
import { ResultButtons } from "./ResultButtons";
import styles from "./contact.module.css";

// CMP ContactPanel (S-04, FR-CNT-001 · 002, 화면 설계 SCR-S04). 목록은 그대로 두고 오른쪽에 연다.
// 입력 칸은 결과와 상관없이 늘 보인다 (#153 결정). 후속 처리 결과 안내는 #154, [문자 보내기]는 #174, 같은 집 일괄 적용은 #179.
const TAG_LABEL: Record<string, string> = {
  NO_CONTACT: "연락 원치 않음",
  SMS_ONLY: "문자만",
  SENSITIVE: "예민함",
  CONTACT_TIME: "연락 선호 시간",
};
const METHOD_LABEL: Record<ContactMethod, string> = { PHONE: "전화", SMS: "문자" };

function itemLabel(serviceType: "VACCINE" | "GROOMING", vaccineTypeName: string | null): string {
  return serviceType === "VACCINE" ? (vaccineTypeName ?? "") : "미용";
}

export function ContactPanel({
  guardianId,
  onSaved,
  onClose,
}: {
  guardianId: number;
  onSaved: () => void;
  onClose: () => void;
}) {
  const today = todayKst();
  const [panel, setPanel] = useState<Panel | null>(null);
  const [history, setHistory] = useState<PageData<ContactHistoryItem> | null>(null);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyError, setHistoryError] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  const [results, setResults] = useState<Results>({});
  const [method, setMethod] = useState<ContactMethod>("PHONE");
  const [talkedWith, setTalkedWith] = useState<TalkedWith>("SELF");
  const [nextDate, setNextDate] = useState("");
  const [nextDateTouched, setNextDateTouched] = useState(false);
  const [memo, setMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);
  const dialog = useRef<HTMLDialogElement>(null);
  // [문자 보내기]는 결과 저장과 따로인 요청이라 키도 따로 (K15)
  const [smsKey, setSmsKey] = useState(newIdempotencyKey);
  const [smsSending, setSmsSending] = useState(false);
  const [smsError, setSmsError] = useState<string | null>(null);
  const nightDialog = useRef<HTMLDialogElement>(null);

  // 보호자가 바뀌면 부르는 쪽이 key={guardianId}로 새로 만든다 (targets/page.tsx). 그래서 입력을 따로 비우지 않는다.
  useEffect(() => {
    let stale = false;
    setLoadError(null);
    getContactPanel(guardianId)
      .then((p) => !stale && setPanel(p))
      .catch((e: unknown) => {
        if (stale) return;
        setPanel(null);
        setLoadError(e instanceof ApiError && e.status === 403 ? e.message : LOAD_FAILED_MESSAGE);
      });
    return () => {
      stale = true;
    };
  }, [guardianId, reload]);

  useEffect(() => {
    let stale = false;
    setHistoryError(false);
    getContactHistory(guardianId, historyPage)
      .then((h) => !stale && setHistory(h))
      .catch(() => {
        if (stale) return;
        setHistory(null);
        setHistoryError(true);
      });
    return () => {
      stale = true;
    };
  }, [guardianId, historyPage, reload]);

  const items = panel?.pets.flatMap((p) => p.items) ?? [];

  // 문자 키는 보낼 항목 목록에 묶는다: 목록이 바뀌면 새 키, 같은 목록을 다시 보내면 같은 키 (K15, PR #245 리뷰).
  // 응답을 못 받은 뒤 목록이 바뀌었는데 같은 키로 보내면 서버가 처음 응답만 돌려줘 새 항목이 기록되지 않는다
  const smsItemIds = items.map((i) => i.scheduleId).join(",");
  useEffect(() => {
    setSmsKey(newIdempotencyKey());
  }, [smsItemIds]);

  function choose(next: Results) {
    setResults(next);
    if (!nextDateTouched) setNextDate(defaultNextContactDate(next, today));
  }

  function chooseAll(result: ContactResult) {
    choose(Object.fromEntries(items.map((i) => [i.scheduleId, result])));
  }

  // 2일차 확인 창은 저장을 누른 때의 최신 주기 상태와 한국 날짜로 판단한다 (PR #225 리뷰):
  // 패널을 연 사이 다른 직원이 남긴 '전화 안 받음'이나 자정을 넘긴 경우에도 확인 없이 주기가 끝나지 않게
  async function requestSave() {
    if (!panel) return;
    setSaving(true);
    let latest = items;
    try {
      const fresh = (await getContactPanel(guardianId)).pets.flatMap((p) => p.items);
      latest = items.map((i) => fresh.find((f) => f.scheduleId === i.scheduleId) ?? i);
    } catch {
      // 못 불러오면 지금 화면 값으로 판단한다. 저장 자체는 서버가 최신 상태로 처리한다
    }
    if (isSecondDayNoAnswer(latest, results, todayKst())) {
      setSaving(false);
      dialog.current?.showModal();
      return;
    }
    void save();
  }

  // 밤 9시~아침 8시면 경고 창을 먼저 띄우고, 보낼 수는 있다 (POL-CNT-007). 누른 순간의 한국 시간으로 판단한다
  function requestSms() {
    if (isNightKst()) nightDialog.current?.showModal();
    else void sendSms();
  }

  // 패널의 예정 항목 전부로 문자 한 통. 문구 종류는 서버가 정한다 (#174 결정)
  async function sendSms() {
    nightDialog.current?.close();
    setSmsSending(true);
    setSmsError(null);
    try {
      const res = await sendMockSms({ guardianId, items: items.map((i) => ({ scheduleId: i.scheduleId })) }, smsKey);
      // 못 보냈어도 기록은 남았다. 다시 누르면 새 기록이라 새 키 (K21)
      setToast(res.sent ? COPY.smsSent : COPY.smsFailed);
      setSmsKey(newIdempotencyKey());
      setReload((n) => n + 1);
      onSaved();
    } catch (e) {
      setSmsError(e instanceof ApiError && e.status >= 400 && e.status < 500 ? e.message : SAVE_FAILED_MESSAGE);
    } finally {
      setSmsSending(false);
    }
  }

  async function save() {
    if (!panel) return;
    dialog.current?.close();
    setSaving(true);
    setSaveError(null);
    try {
      const saved = await saveContact(
        {
          guardianId,
          method,
          talkedWith: method === "PHONE" ? talkedWith : null,
          memo: memo.trim() || null,
          nextContactDate: nextDate || null,
          items: items.map((i) => ({ scheduleId: i.scheduleId, result: results[i.scheduleId] as ContactResult })),
          applyToHousehold: false,
        },
        idempotencyKey,
      );
      setToast(savedToast(saved.followUp));
      setIdempotencyKey(newIdempotencyKey());
      setResults({});
      setNextDate("");
      setNextDateTouched(false);
      setMemo("");
      setReload((n) => n + 1);
      onSaved();
    } catch (e) {
      // 자동으로 다시 보내지 않고 입력을 둔다 (POL-ERR-002). 4xx는 서버 문구(입력 확인 · 무지개다리 등)
      setSaveError(e instanceof ApiError && e.status >= 400 && e.status < 500 ? e.message : SAVE_FAILED_MESSAGE);
    } finally {
      setSaving(false);
    }
  }

  const g = panel?.guardian;
  const secondDay = g ? secondDayCopy(g.smsAgreed) : null;
  const hasSecondDay = items.some((i) => isSecondDayItem(i, today));

  return (
    <aside className={styles.panel} aria-label="연락 패널">
      <div className={styles.head}>
        <h2>{g ? g.name : "연락 패널"}</h2>
        <Button variant="ghost" size="sm" onClick={onClose}>
          닫기
        </Button>
      </div>

      {loadError && <Notice tone="danger">{loadError}</Notice>}

      {g && (
        <>
          <dl className={styles.info}>
            <dt>번호</dt>
            <dd>
              {formatPhone(g.phone)} {g.phoneStatus === "WRONG" && <Badge tone="danger">번호 오류</Badge>}
            </dd>
            {g.subPhone && (
              <>
                <dt>보조 연락처</dt>
                <dd>{formatPhone(g.subPhone)}</dd>
              </>
            )}
            {g.preferredContact && (
              <>
                <dt>선호 연락</dt>
                <dd>{METHOD_LABEL[g.preferredContact]}</dd>
              </>
            )}
          </dl>
          {g.tags.length > 0 && (
            <p className={styles.tags}>
              {g.tags.map((t) => (
                <Badge key={t} tone="warning">
                  {t === "CONTACT_TIME" && g.contactTimeNote ? `${TAG_LABEL[t]}: ${g.contactTimeNote}` : TAG_LABEL[t] ?? t}
                </Badge>
              ))}
            </p>
          )}

          {panel.todayContact && (
            <Notice tone="info">{todayContactMessage(panel.todayContact.staffName, panel.todayContact.time)}</Notice>
          )}
          {hasSecondDay && secondDay && <Notice tone="warning">{secondDay.notice}</Notice>}

          {items.length > 0 && (
            <form
              className={styles.form}
              onSubmit={(e) => {
                e.preventDefault();
                void requestSave();
              }}
            >
              <ResultButtons label="모든 항목에 같은 결과" value={undefined} onChange={chooseAll} />
              <ul className={styles.items}>
                {panel.pets.map((pet) =>
                  pet.items.map((i) => (
                    <li key={i.scheduleId} className={styles.item}>
                      <span className={styles.itemName}>
                        {pet.name} · {itemLabel(i.serviceType, i.vaccineTypeName)} · {i.dueDate}{" "}
                        {i.missed && <Badge tone="danger">누락</Badge>}
                      </span>
                      <ResultButtons
                        label={`${pet.name} ${itemLabel(i.serviceType, i.vaccineTypeName)} 결과`}
                        value={results[i.scheduleId]}
                        onChange={(r) => choose({ ...results, [i.scheduleId]: r })}
                      />
                    </li>
                  )),
                )}
              </ul>

              <SegmentedControl<ContactMethod>
                label="연락 방법"
                options={[
                  { value: "PHONE", label: "전화" },
                  { value: "SMS", label: "문자", disabled: !g.smsAgreed },
                ]}
                value={method}
                onChange={setMethod}
              />
              {!g.smsAgreed && <p className={styles.hint}>{COPY.smsNotAgreed}</p>}
              {method === "PHONE" && (
                <SegmentedControl<TalkedWith>
                  label="통화한 사람"
                  options={[
                    { value: "SELF", label: "본인" },
                    { value: "FAMILY", label: "가족" },
                  ]}
                  value={talkedWith}
                  onChange={setTalkedWith}
                />
              )}
              <TextField
                label="다음 연락 예정일"
                type="date"
                value={nextDate}
                min={today}
                onChange={(v) => {
                  setNextDate(v);
                  setNextDateTouched(true);
                }}
              />
              <label className={styles.memo}>
                <span>메모</span>
                <textarea value={memo} maxLength={MEMO_MAX} rows={3} onChange={(e) => setMemo(e.target.value)} />
                <span className={styles.hint}>{COPY.memoGuide}</span>
              </label>

              {saveError && <Notice tone="danger">{saveError}</Notice>}
              {/* 결과 저장과 문자 보내기는 한쪽이 진행 중이면 다른 쪽을 끈다 (PR #245 리뷰) */}
              <Button type="submit" loading={saving} disabled={smsSending || !canSave(items, results, memo)}>
                연락 결과 저장
              </Button>

              {/* 수신 동의가 없으면 꺼지고 안내는 위 연락 방법 아래에 있다 (SCR-S04-03). 서버도 409로 막는다 */}
              <Button variant="secondary" loading={smsSending} disabled={saving || !g.smsAgreed} onClick={requestSms}>
                문자 보내기
              </Button>
              <p className={styles.hint}>{COPY.smsNote}</p>
              {smsError && <Notice tone="danger">{smsError}</Notice>}
            </form>
          )}

          <dialog ref={nightDialog} className={styles.dialog} aria-labelledby="night-sms-title">
            <p id="night-sms-title">{COPY.nightWarning}</p>
            <div className={styles.dialogActions}>
              <Button variant="secondary" onClick={() => nightDialog.current?.close()}>
                취소
              </Button>
              <Button onClick={() => void sendSms()}>문자 보내기</Button>
            </div>
          </dialog>

          <dialog ref={dialog} className={styles.dialog} aria-labelledby="second-day-title">
            {secondDay && (
              <>
                <p id="second-day-title">{secondDay.notice}</p>
                {secondDay.detail && <p className={styles.hint}>{secondDay.detail}</p>}
                <div className={styles.dialogActions}>
                  <Button variant="secondary" onClick={() => dialog.current?.close()}>
                    취소
                  </Button>
                  <Button onClick={() => void save()}>{secondDay.confirm}</Button>
                </div>
              </>
            )}
          </dialog>

          <section className={styles.history} aria-label="지난 연락">
            <h3>지난 연락</h3>
            {historyError && (
              <Notice
                tone="danger"
                actions={
                  <Button variant="ghost" size="sm" onClick={() => setReload((n) => n + 1)}>
                    다시 시도
                  </Button>
                }
              >
                {LOAD_FAILED_MESSAGE}
              </Notice>
            )}
            {history && history.total === 0 && <p className={styles.hint}>{COPY.emptyHistory}</p>}
            {history && history.items.length > 0 && (
              <ul>
                {history.items.map((h) => (
                  <li key={h.id} className={styles.historyRow}>
                    <span>
                      {h.date} · {h.petName} · {h.summary}
                    </span>
                    {h.memo && <span className={styles.hint}>{h.memo}</span>}
                    <span className={styles.hint}>
                      {h.staffName} · {h.recordedAt.slice(11, 16)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            {history && history.total > history.size && (
              <nav className={styles.pager} aria-label="지난 연락 페이지">
                {historyPage > 1 && (
                  <Button variant="ghost" size="sm" onClick={() => setHistoryPage(historyPage - 1)}>
                    이전
                  </Button>
                )}
                {historyPage * history.size < history.total && (
                  <Button variant="ghost" size="sm" onClick={() => setHistoryPage(historyPage + 1)}>
                    다음
                  </Button>
                )}
              </nav>
            )}
          </section>
        </>
      )}

      {toast && <Toast message={toast} onClose={() => setToast(null)} />}
    </aside>
  );
}
