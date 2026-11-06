import type { Metadata } from "next";
import type { AuditEntry } from "@lms/api-client";
import { ErrorState, PageHeader, Panel } from "@lms/ui";

import { ApiError, apiFetch } from "@/server/api";
import { requireMe } from "@/server/me";

import { AuditTable } from "./audit-table";

export const metadata: Metadata = { title: "감사 로그" };

export default async function AuditPage() {
  const me = await requireMe();
  if (me.role !== "ORG_ADMIN") {
    return (
      <>
        <PageHeader title="감사 로그" />
        <Panel>
          <ErrorState title="조직 관리자만 볼 수 있는 화면입니다" description="감사 기록이 필요하면 조직 관리자에게 요청하세요." />
        </Panel>
      </>
    );
  }
  let entries: AuditEntry[] | null = null;
  let traceId: string | undefined;
  try {
    entries = await apiFetch<AuditEntry[]>("/v1/audit-log?limit=200");
  } catch (error) {
    if (!(error instanceof ApiError)) throw error;
    traceId = error.traceId;
  }
  return (
    <>
      <PageHeader
        title="감사 로그"
        description="누가 언제 무엇을 바꿨는지 남는 기록입니다. 기록은 수정하거나 지울 수 없습니다."
      />
      {entries ? (
        <AuditTable entries={entries} />
      ) : (
        <Panel>
          <ErrorState title="감사 로그를 불러오지 못했습니다" description="잠시 후 다시 시도해 주세요." traceId={traceId} />
        </Panel>
      )}
    </>
  );
}
