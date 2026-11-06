"use client";

import * as React from "react";
import { ClipboardCheck, Plus, Save, Trash2, X } from "lucide-react";
import type { CourseDetail, QuizView } from "@lms/api-client";
import { Button, EmptyState, Field, Input, Panel, PanelBody, PanelFooter, PanelHeader, Select, Textarea } from "@lms/ui";

type DraftQuestion = { key: string; type: "SINGLE" | "MULTIPLE"; prompt: string; options: string[]; correct: number[]; points: number };
type Draft = { passScore: number; maxAttempts: string; timeLimitMin: string; questions: DraftQuestion[] };

const blankQuestion = (): DraftQuestion => ({
  key: crypto.randomUUID(),
  type: "SINGLE",
  prompt: "",
  options: ["", ""],
  correct: [0],
  points: 10,
});

function toDraft(quiz: QuizView | null | undefined): Draft {
  if (!quiz) return { passScore: 80, maxAttempts: "3", timeLimitMin: "", questions: [blankQuestion()] };
  return {
    passScore: quiz.passScore,
    maxAttempts: quiz.maxAttempts ? String(quiz.maxAttempts) : "",
    timeLimitMin: quiz.timeLimitSec ? String(Math.round(quiz.timeLimitSec / 60)) : "",
    questions: quiz.questions.map((q) => ({
      key: q.id,
      type: q.type,
      prompt: q.prompt,
      options: q.options.map((o) => o.text),
      correct: q.answer.map((id) => q.options.findIndex((o) => o.id === id)).filter((i) => i >= 0),
      points: q.points,
    })),
  };
}

/**
 * The course's final quiz, saved as a whole (PUT with If-Match, like every course write).
 * The server assigns option ids and re-validates everything; this form only helps.
 */
export function QuizEditor({
  quiz,
  editable,
  onSave,
  onDelete,
}: {
  quiz: QuizView | null | undefined;
  editable: boolean;
  onSave: (body: unknown) => Promise<CourseDetail | null>;
  onDelete: () => Promise<CourseDetail | null>;
}) {
  const [draft, setDraft] = React.useState<Draft | null>(quiz ? toDraft(quiz) : null);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const update = (key: string, patch: Partial<DraftQuestion>) =>
    setDraft((d) => d && { ...d, questions: d.questions.map((q) => (q.key === key ? { ...q, ...patch } : q)) });

  async function save() {
    if (!draft) return;
    const empty = draft.questions.findIndex((q) => !q.prompt.trim() || q.options.some((o) => !o.trim()));
    if (empty >= 0) return setError(`${empty + 1}번 문항에 비어 있는 문제나 보기가 있습니다.`);
    const noAnswer = draft.questions.findIndex((q) => q.correct.length === 0);
    if (noAnswer >= 0) return setError(`${noAnswer + 1}번 문항의 정답을 고르세요.`);
    setError(null);
    setSaving(true);
    await onSave({
      passScore: draft.passScore,
      maxAttempts: draft.maxAttempts ? Number(draft.maxAttempts) : null,
      timeLimitSec: draft.timeLimitMin ? Number(draft.timeLimitMin) * 60 : null,
      questions: draft.questions.map((q) => ({
        type: q.type,
        prompt: q.prompt.trim(),
        options: q.options.map((o) => o.trim()),
        correct: q.correct,
        points: q.points,
      })),
    });
    setSaving(false);
  }

  if (!draft) {
    return (
      <Panel>
        <PanelHeader title="최종 퀴즈" />
        <EmptyState
          icon={ClipboardCheck}
          title="퀴즈가 없는 코스입니다"
          description="퀴즈가 없으면 마지막 레슨을 마칠 때 수료합니다. 퀴즈를 넣으면 통과해야 수료합니다."
          action={
            editable ? (
              <Button variant="secondary" onClick={() => setDraft(toDraft(null))}>
                <Plus aria-hidden="true" />
                퀴즈 추가
              </Button>
            ) : undefined
          }
        />
      </Panel>
    );
  }

  return (
    <Panel>
      <PanelHeader
        title="최종 퀴즈"
        description={quiz ? `저장된 버전 ${quiz.revision} · 문항 ${quiz.questions.length}개` : "아직 저장하지 않았습니다"}
      />
      <PanelBody className="grid gap-6">
        <fieldset disabled={!editable} className="grid gap-4 sm:grid-cols-3">
          <Field label="통과 점수(%)">
            <Input
              type="number"
              min={1}
              max={100}
              value={draft.passScore}
              onChange={(e) => setDraft({ ...draft, passScore: Number(e.target.value) })}
            />
          </Field>
          <Field label="최대 응시 횟수" optional hint="비우면 무제한">
            <Input type="number" min={1} max={20} value={draft.maxAttempts} onChange={(e) => setDraft({ ...draft, maxAttempts: e.target.value })} />
          </Field>
          <Field label="제한 시간(분)" optional hint="비우면 제한 없음">
            <Input type="number" min={1} max={240} value={draft.timeLimitMin} onChange={(e) => setDraft({ ...draft, timeLimitMin: e.target.value })} />
          </Field>
        </fieldset>

        <ol className="grid gap-4">
          {draft.questions.map((q, index) => (
            <li key={q.key} className="rounded-panel border border-line p-4">
              <fieldset disabled={!editable} className="grid gap-3">
                <legend className="sr-only">{index + 1}번 문항</legend>
                <div className="flex flex-wrap items-end gap-3">
                  <span className="pb-2 text-label font-semibold text-ink-2">{index + 1}.</span>
                  <Field label="유형" className="w-36">
                    <Select
                      value={q.type}
                      onChange={(e) => {
                        const type = e.target.value as DraftQuestion["type"];
                        update(q.key, { type, correct: type === "SINGLE" ? q.correct.slice(0, 1) : q.correct });
                      }}
                    >
                      <option value="SINGLE">단일 선택</option>
                      <option value="MULTIPLE">복수 선택</option>
                    </Select>
                  </Field>
                  <Field label="배점" className="w-24">
                    <Input type="number" min={1} max={100} value={q.points} onChange={(e) => update(q.key, { points: Number(e.target.value) })} />
                  </Field>
                  <span className="flex-1" />
                  {draft.questions.length > 1 ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      aria-label={`${index + 1}번 문항 삭제`}
                      onClick={() => setDraft({ ...draft, questions: draft.questions.filter((x) => x.key !== q.key) })}
                    >
                      <Trash2 aria-hidden="true" />
                    </Button>
                  ) : null}
                </div>
                <Field label="문제">
                  <Textarea rows={2} value={q.prompt} maxLength={2000} onChange={(e) => update(q.key, { prompt: e.target.value })} />
                </Field>
                <div className="grid gap-2">
                  <p className="text-label font-medium text-ink">보기 ({q.type === "SINGLE" ? "정답 하나" : "정답 모두"}를 표시)</p>
                  {q.options.map((option, oi) => (
                    <div key={oi} className="flex items-center gap-2">
                      <input
                        type={q.type === "SINGLE" ? "radio" : "checkbox"}
                        name={`correct-${q.key}`}
                        aria-label={`보기 ${oi + 1} 정답`}
                        className="size-4 accent-[var(--accent)]"
                        checked={q.correct.includes(oi)}
                        onChange={(e) =>
                          update(q.key, {
                            correct:
                              q.type === "SINGLE"
                                ? [oi]
                                : e.target.checked
                                  ? [...q.correct, oi].sort()
                                  : q.correct.filter((c) => c !== oi),
                          })
                        }
                      />
                      <Input
                        aria-label={`보기 ${oi + 1}`}
                        value={option}
                        maxLength={500}
                        onChange={(e) => update(q.key, { options: q.options.map((o, i) => (i === oi ? e.target.value : o)) })}
                      />
                      {q.options.length > 2 ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          aria-label={`보기 ${oi + 1} 삭제`}
                          onClick={() =>
                            update(q.key, {
                              options: q.options.filter((_, i) => i !== oi),
                              correct: q.correct.filter((c) => c !== oi).map((c) => (c > oi ? c - 1 : c)),
                            })
                          }
                        >
                          <X aria-hidden="true" />
                        </Button>
                      ) : null}
                    </div>
                  ))}
                  {editable && q.options.length < 8 ? (
                    <Button type="button" variant="ghost" size="sm" className="justify-self-start" onClick={() => update(q.key, { options: [...q.options, ""] })}>
                      <Plus aria-hidden="true" />
                      보기 추가
                    </Button>
                  ) : null}
                </div>
              </fieldset>
            </li>
          ))}
        </ol>
        {editable && draft.questions.length < 50 ? (
          <Button
            type="button"
            variant="secondary"
            className="justify-self-start"
            onClick={() => setDraft({ ...draft, questions: [...draft.questions, blankQuestion()] })}
          >
            <Plus aria-hidden="true" />
            문항 추가
          </Button>
        ) : null}
      </PanelBody>
      {editable ? (
        <PanelFooter className="flex-wrap justify-between">
          <div className="min-w-0 flex-1">{error ? <p className="text-label text-danger">{error}</p> : null}</div>
          <div className="flex gap-2">
            {quiz ? (
              <Button variant="ghost" onClick={() => void onDelete()}>
                퀴즈 삭제
              </Button>
            ) : (
              <Button variant="ghost" onClick={() => setDraft(null)}>
                취소
              </Button>
            )}
            <Button onClick={save} loading={saving}>
              <Save aria-hidden="true" />
              퀴즈 저장
            </Button>
          </div>
        </PanelFooter>
      ) : null}
    </Panel>
  );
}
