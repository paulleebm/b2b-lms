"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import type { Department, InviteResult, MemberView } from "@lms/api-client";
import { AdminMembers, Notice, type InviteInput, type MemberRole, type MemberRow } from "@lms/ui";

import { api, describeError } from "@/lib/client-api";

type Problem = { title: string; detail?: string; traceId?: string };

/** API rows → the screen's rows. The screen calls a left member "INACTIVE"; the API says DISABLED. */
function toRow(m: MemberView, selfUserId: string): MemberRow {
  return {
    id: m.id,
    name: m.name ?? "",
    email: m.email,
    departmentId: m.departmentId ?? undefined,
    role: m.role,
    status: m.status === "DISABLED" ? "INACTIVE" : m.status,
    isSelf: m.userId === selfUserId,
  };
}

function summarize(results: InviteResult[]): { message: string; flagged: InviteResult[] } {
  const invited = results.filter((r) => r.outcome === "INVITED");
  const resent = invited.filter((r) => r.message === "안내 메일을 다시 보냈습니다.").length;
  const parts = [`${invited.length - resent}명 초대`];
  if (resent) parts.push(`${resent}명에게 메일 재발송`);
  const already = results.filter((r) => r.outcome === "ALREADY_MEMBER").length;
  if (already) parts.push(`${already}명은 이미 멤버`);
  const failed = results.filter((r) => r.outcome === "FAILED").length;
  if (failed) parts.push(`${failed}명 실패`);
  // Failures, and invitations saved without their email, need a person's attention.
  const flagged = results.filter(
    (r) => r.outcome === "FAILED" || (r.outcome === "INVITED" && r.message && r.message !== "안내 메일을 다시 보냈습니다."),
  );
  return { message: `${parts.join(", ")}.`, flagged };
}

export function MembersClient({
  members,
  departments,
  selfUserId,
}: {
  members: MemberView[];
  departments: Department[];
  selfUserId: string;
}) {
  const router = useRouter();
  const [, startRefresh] = useTransition();
  const [problem, setProblem] = useState<Problem | null>(null);
  const [failedInvites, setFailedInvites] = useState<InviteResult[]>([]);

  // Every change re-reads the list from the server, so the table shows what the API decided
  // (an optimistic change the API refused snaps back, with the reason above the table).
  const refresh = () => startRefresh(() => router.refresh());

  async function run(action: () => Promise<unknown>) {
    setProblem(null);
    try {
      await action();
    } catch (error) {
      setProblem(describeError(error));
    } finally {
      refresh();
    }
  }

  async function invite(input: InviteInput): Promise<string> {
    setProblem(null);
    setFailedInvites([]);
    try {
      const { data } = await api<{ results: InviteResult[] }>("POST", "/v1/invitations", { body: input });
      const { message, flagged } = summarize(data.results);
      setFailedInvites(flagged);
      refresh();
      return message;
    } catch (error) {
      setProblem(describeError(error));
      throw error;
    }
  }

  const patch = (id: string, body: Record<string, unknown>) =>
    run(() => api("PATCH", `/v1/members/${id}`, { body }));

  const notice = (
    <>
      {problem ? (
        <Notice tone="danger" title={problem.title}>
          {problem.detail}
          {problem.traceId ? <span className="block text-caption text-muted">추적 ID {problem.traceId}</span> : null}
        </Notice>
      ) : null}
      {failedInvites.length > 0 ? (
        <Notice tone="warning" title={`${failedInvites.length}명은 확인이 필요합니다`}>
          <ul className="mt-1 grid gap-0.5">
            {failedInvites.map((r) => (
              <li key={r.email}>
                <span className="font-medium text-ink">{r.email}</span>: {r.message}
              </li>
            ))}
          </ul>
        </Notice>
      ) : null}
    </>
  );

  return (
    <AdminMembers
      members={members.map((m) => toRow(m, selfUserId))}
      departments={departments.map((d) => ({ id: d.id, name: d.name }))}
      notice={notice}
      onInvite={invite}
      onRoleChange={(id, role: MemberRole) => patch(id, { role })}
      onDeactivate={(id) => patch(id, { status: "DISABLED" })}
      onReactivate={(id) => patch(id, { status: "ACTIVE" })}
      onRevokeInvite={(id) => run(() => api("DELETE", `/v1/members/${id}`))}
    />
  );
}
