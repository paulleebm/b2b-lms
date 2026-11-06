"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Check, Pencil, Plus, Trash2, X } from "lucide-react";
import type { Department } from "@lms/api-client";
import { Button, EmptyState, Field, Input, Notice, PageHeader, Panel, PanelFooter, PanelHeader } from "@lms/ui";

import { api, describeError } from "@/lib/client-api";

/** Departments: create, rename, delete. A department with members cannot be deleted (409). */
export function DepartmentsClient({ departments }: { departments: Department[] }) {
  const router = useRouter();
  const [name, setName] = React.useState("");
  const [editing, setEditing] = React.useState<string | null>(null);
  const [draft, setDraft] = React.useState("");
  const [confirming, setConfirming] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [problem, setProblem] = React.useState<string | null>(null);
  const [nameError, setNameError] = React.useState<string | undefined>();

  async function run(action: () => Promise<unknown>) {
    setProblem(null);
    setBusy(true);
    try {
      await action();
      router.refresh();
      return true;
    } catch (error) {
      setProblem(describeError(error).title);
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function create(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return setNameError("부서 이름을 입력하세요.");
    setNameError(undefined);
    if (await run(() => api("POST", "/v1/departments", { body: { name: name.trim() } }))) setName("");
  }

  return (
    <div className="mx-auto max-w-[720px]">
      <PageHeader title="부서" description="부서별로 교육을 배정합니다. 멤버의 부서는 멤버 화면에서 바꿉니다." />
      <div className="grid gap-6">
        {problem ? <Notice tone="danger" title={problem} /> : null}
        <Panel>
          <PanelHeader title="새 부서" />
          <form noValidate onSubmit={create}>
            <div className="px-5 pb-1">
              <Field label="부서 이름" error={nameError}>
                <Input value={name} maxLength={100} onChange={(e) => setName(e.target.value)} placeholder="예: 개발팀" />
              </Field>
            </div>
            <PanelFooter className="justify-end">
              <Button type="submit" loading={busy && editing === null && confirming === null}>
                <Plus aria-hidden="true" />
                만들기
              </Button>
            </PanelFooter>
          </form>
        </Panel>

        <Panel>
          <PanelHeader title="부서 목록" description={`${departments.length}개`} />
          {departments.length === 0 ? (
            <EmptyState icon={Plus} title="아직 부서가 없습니다" description="위에서 첫 부서를 만드세요." />
          ) : (
            <ul className="divide-y divide-line border-t border-line">
              {departments.map((d) => (
                <li key={d.id} className="flex items-center gap-2 px-5 py-3">
                  {editing === d.id ? (
                    <form
                      className="flex flex-1 items-center gap-2"
                      onSubmit={async (e) => {
                        e.preventDefault();
                        if (draft.trim() && (await run(() => api("PATCH", `/v1/departments/${d.id}`, { body: { name: draft.trim() } })))) {
                          setEditing(null);
                        }
                      }}
                    >
                      <Input autoFocus aria-label={`${d.name} 새 이름`} value={draft} maxLength={100} onChange={(e) => setDraft(e.target.value)} />
                      <Button type="submit" size="sm" aria-label="이름 저장" loading={busy}>
                        <Check aria-hidden="true" />
                      </Button>
                      <Button type="button" size="sm" variant="ghost" aria-label="취소" onClick={() => setEditing(null)}>
                        <X aria-hidden="true" />
                      </Button>
                    </form>
                  ) : (
                    <>
                      <span className="min-w-0 flex-1 truncate text-body text-ink">{d.name}</span>
                      {confirming === d.id ? (
                        <span role="group" aria-label={`${d.name} 삭제 확인`} className="inline-flex gap-1">
                          <Button size="sm" variant="ghost" autoFocus onClick={() => setConfirming(null)}>
                            취소
                          </Button>
                          <Button
                            size="sm"
                            variant="danger"
                            loading={busy}
                            onClick={async () => {
                              await run(() => api("DELETE", `/v1/departments/${d.id}`));
                              setConfirming(null);
                            }}
                          >
                            삭제
                          </Button>
                        </span>
                      ) : (
                        <>
                          <Button
                            size="sm"
                            variant="ghost"
                            aria-label={`${d.name} 이름 바꾸기`}
                            onClick={() => {
                              setEditing(d.id);
                              setDraft(d.name);
                            }}
                          >
                            <Pencil aria-hidden="true" />
                          </Button>
                          <Button size="sm" variant="ghost" aria-label={`${d.name} 삭제`} onClick={() => setConfirming(d.id)}>
                            <Trash2 aria-hidden="true" />
                          </Button>
                        </>
                      )}
                    </>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  );
}
