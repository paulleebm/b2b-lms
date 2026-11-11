"use client";

import * as React from "react";
import { CircleCheck, CircleMinus, Mail, Send, UserRoundPlus, type LucideIcon } from "lucide-react";

import { cn } from "../lib/utils";
import { AppShell, PageHeader } from "../components/app-shell";
import { Button } from "../components/button";
import { DataTable, type Column } from "../components/data-table";
import { Field, Select, Textarea } from "../components/field";
import { Notice } from "../components/notice";
import { Panel, PanelBody, PanelFooter, PanelHeader } from "../components/panel";
import { Badge } from "../components/status-badge";
import { EmptyState } from "../components/states";
import {
  departments as defaultDepartments,
  members as defaultMembers,
  roleLabel,
  type Department,
  type MemberRole,
  type MemberRow,
  type MemberStatus,
} from "./fixtures";
import { AccountArea, WorkspaceSwitcher, adminNav, withCurrent } from "./shell-presets";

/**
 * Admin "멤버" — invite people, then manage role and status (DESIGN.md §4 Tables, Forms; §9).
 * Presentational: every change goes out through a callback. Local state only mirrors the
 * change so the demo responds; the real app re-reads the server (the source of truth).
 *
 * Form validation follows craft/form-validation: first blur → re-validate on every input while
 * invalid → validate everything on submit and focus the first error. Submit is never disabled
 * for invalid input; the error says what to fix.
 */

type ScreenState = "populated" | "loading" | "error";
type StatusFilter = "ALL" | MemberStatus;

type InviteInput = { emails: string[]; role: MemberRole; departmentId?: string };

type AdminMembersScreenProps = {
  state?: ScreenState;
  members?: MemberRow[];
  departments?: Department[];
  /**
   * Return a Promise to drive the form from the real API: it resolves to the result line shown
   * under the form (e.g. "3명 초대, 1명은 이미 멤버"); a rejection keeps the input for a retry.
   * Without a Promise the screen simulates the round trip (stories).
   */
  onInvite?: (input: InviteInput) => void | Promise<string | void>;
  onRoleChange?: (memberId: string, role: MemberRole) => void;
  onDeactivate?: (memberId: string) => void;
  onReactivate?: (memberId: string) => void;
  onRevokeInvite?: (memberId: string) => void;
};

const MAX_INVITES = 50;
const EMAIL = /^[^\s@,;]+@[^\s@,;]+\.[^\s@,;]+$/;

const statusView: Record<MemberStatus, { tone: "success" | "info" | "neutral"; text: string; icon: LucideIcon }> = {
  ACTIVE: { tone: "success", text: "활성", icon: CircleCheck },
  INVITED: { tone: "info", text: "초대됨", icon: Mail },
  INACTIVE: { tone: "neutral", text: "비활성", icon: CircleMinus },
};

function MemberStatusBadge({ status }: { status: MemberStatus }) {
  const view = statusView[status];
  const Icon = view.icon;
  return (
    <Badge tone={view.tone}>
      <Icon className="size-3 stroke-[2]" aria-hidden="true" />
      {view.text}
    </Badge>
  );
}

function parseEmails(raw: string): string[] {
  return raw
    .split(/[\s,;]+/)
    .map((e) => e.trim())
    .filter(Boolean);
}

function validateEmails(raw: string, existing: MemberRow[]): string | undefined {
  const emails = parseEmails(raw);
  if (emails.length === 0) return "초대할 이메일을 한 개 이상 입력하세요.";
  const invalid = emails.filter((e) => !EMAIL.test(e));
  const list = (xs: string[]) => `${xs.slice(0, 3).join(", ")}${xs.length > 3 ? ` 외 ${xs.length - 3}개` : ""}`;
  if (invalid.length > 0) return `형식이 올바르지 않은 이메일이 있습니다: ${list(invalid)}`;
  if (emails.length > MAX_INVITES) return `한 번에 ${MAX_INVITES}명까지 초대할 수 있습니다. 지금 ${emails.length}명입니다.`;
  // Pending invitees may be invited again (the email is re-sent) and disabled members re-invited;
  // only people who are already active are a mistake.
  const active = new Set(existing.filter((m) => m.status === "ACTIVE").map((m) => m.email.toLowerCase()));
  const dupes = emails.filter((e) => active.has(e.toLowerCase()));
  if (dupes.length > 0) return `이미 활성 멤버인 이메일입니다: ${list(dupes)}`;
  return undefined;
}

/** Name, or the email when an invitee has not signed up yet (missing names must not break the row). */
const displayName = (m: MemberRow) => m.name || m.email;

type AdminMembersProps = AdminMembersScreenProps & {
  /** Shown above the page content, e.g. an API error from the last action. */
  notice?: React.ReactNode;
  /** Marks the page as synthetic data (stories only). */
  demo?: boolean;
};

/** The page content without the app shell, for the real app (which owns its shell). */
function AdminMembers({
  state = "populated",
  members = defaultMembers,
  departments = defaultDepartments,
  onInvite,
  onRoleChange,
  onDeactivate,
  onReactivate,
  onRevokeInvite,
  notice,
  demo = false,
}: AdminMembersProps) {
  const [rows, setRows] = React.useState(members);
  React.useEffect(() => setRows(members), [members]);

  const [filter, setFilter] = React.useState<StatusFilter>("ALL");
  const [confirmingId, setConfirmingId] = React.useState<string | null>(null);
  const [announcement, setAnnouncement] = React.useState("");

  // Invite form
  const emailsRef = React.useRef<HTMLTextAreaElement>(null);
  const [emails, setEmails] = React.useState("");
  const [role, setRole] = React.useState<MemberRole>("LEARNER");
  const [departmentId, setDepartmentId] = React.useState("");
  const [touched, setTouched] = React.useState(false);
  const [emailError, setEmailError] = React.useState<string | undefined>();
  const [inviting, setInviting] = React.useState(false);
  const [inviteResult, setInviteResult] = React.useState<string | null>(null);

  const self = rows.find((m) => m.isSelf);
  const departmentName = (id?: string) => departments.find((d) => d.id === id)?.name;
  const counts = React.useMemo(
    () => ({
      ALL: rows.length,
      ACTIVE: rows.filter((m) => m.status === "ACTIVE").length,
      INVITED: rows.filter((m) => m.status === "INVITED").length,
      INACTIVE: rows.filter((m) => m.status === "INACTIVE").length,
    }),
    [rows],
  );
  const visible = filter === "ALL" ? rows : rows.filter((m) => m.status === filter);
  const onlySelf = state === "populated" && rows.length === 1 && rows[0]?.isSelf;

  const update = (id: string, patch: Partial<MemberRow>) =>
    setRows((prev) => prev.map((m) => (m.id === id ? { ...m, ...patch } : m)));

  const submitInvite = (e: React.FormEvent) => {
    e.preventDefault();
    setInviteResult(null);
    const error = validateEmails(emails, rows);
    setTouched(true);
    setEmailError(error);
    if (error) {
      emailsRef.current?.focus();
      return;
    }
    const list = parseEmails(emails);
    const input: InviteInput = { emails: list, role, departmentId: departmentId || undefined };
    const pending = onInvite?.(input);
    setInviting(true);
    if (pending instanceof Promise) {
      pending
        .then((message) => {
          setEmails("");
          setTouched(false);
          setInviteResult(message || `${list.length}명에게 초대 메일을 보냈습니다.`);
        })
        .catch(() => undefined) // the caller reports the error; the input stays for a retry
        .finally(() => setInviting(false));
      return;
    }
    window.setTimeout(() => {
      setRows((prev) => [
        ...prev,
        ...list.map(
          (email, i): MemberRow => ({
            id: `inv-${Date.now()}-${i}`,
            name: "",
            email,
            departmentId: input.departmentId,
            role,
            status: "INVITED",
          }),
        ),
      ]);
      setInviting(false);
      // Success clears the input; a failure would keep it (craft/form-validation).
      setEmails("");
      setTouched(false);
      setInviteResult(`${list.length}명에게 초대 메일을 보냈습니다.`);
    }, 600);
  };

  const columns: Column<MemberRow>[] = [
    {
      key: "member",
      header: "멤버",
      cell: (m) => (
        <span className="grid min-w-0">
          <span className="flex min-w-0 items-center gap-1.5">
            <span className="truncate font-medium text-ink" title={displayName(m)}>
              {displayName(m)}
            </span>
            {m.isSelf ? <Badge className="shrink-0">나</Badge> : null}
          </span>
          {m.name ? (
            <span className="truncate text-caption text-muted" title={m.email}>
              {m.email}
            </span>
          ) : (
            <span className="text-caption text-muted">가입 전</span>
          )}
        </span>
      ),
    },
    {
      key: "department",
      header: "부서",
      secondary: true,
      width: "w-[152px]",
      cell: (m) => {
        const name = departmentName(m.departmentId);
        return name ? <span title={name}>{name}</span> : <span className="text-muted">미지정</span>;
      },
    },
    {
      key: "role",
      header: "역할",
      width: "w-[148px]",
      cell: (m) => (
        <Select
          size="sm"
          aria-label={`${displayName(m)} 역할`}
          value={m.role}
          disabled={m.isSelf}
          aria-describedby={m.isSelf ? "members-self-note" : undefined}
          onChange={(e) => {
            const next = e.target.value as MemberRole;
            update(m.id, { role: next });
            onRoleChange?.(m.id, next);
            setAnnouncement(`${displayName(m)}님의 역할을 ${roleLabel[next]}(으)로 바꿨습니다.`);
          }}
        >
          {(Object.keys(roleLabel) as MemberRole[]).map((r) => (
            <option key={r} value={r}>
              {roleLabel[r]}
            </option>
          ))}
        </Select>
      ),
    },
    { key: "status", header: "상태", width: "w-[96px]", cell: (m) => <MemberStatusBadge status={m.status} /> },
    {
      key: "action",
      header: "동작",
      align: "right",
      width: "w-[184px]",
      cell: (m) => {
        const name = displayName(m);
        if (m.isSelf) {
          return (
            <Button variant="ghost" size="sm" disabled aria-describedby="members-self-note">
              비활성화
            </Button>
          );
        }
        if (m.status === "ACTIVE") {
          if (confirmingId === m.id) {
            return (
              <span
                role="group"
                aria-label={`${name} 비활성화 확인`}
                className="inline-flex items-center gap-1"
                onKeyDown={(e) => e.key === "Escape" && setConfirmingId(null)}
              >
                <Button variant="ghost" size="sm" autoFocus onClick={() => setConfirmingId(null)}>
                  취소
                </Button>
                <Button
                  variant="danger"
                  size="sm"
                  aria-label={`${name} 비활성화 확정`}
                  onClick={() => {
                    setConfirmingId(null);
                    update(m.id, { status: "INACTIVE" });
                    onDeactivate?.(m.id);
                    setAnnouncement(`${name}님을 비활성화했습니다. 더 이상 로그인할 수 없습니다.`);
                  }}
                >
                  비활성화
                </Button>
              </span>
            );
          }
          return (
            <Button variant="ghost" size="sm" aria-label={`${name} 비활성화`} onClick={() => setConfirmingId(m.id)}>
              비활성화
            </Button>
          );
        }
        if (m.status === "INVITED") {
          return (
            <Button
              variant="ghost"
              size="sm"
              aria-label={`${name} 초대 취소`}
              onClick={() => {
                setRows((prev) => prev.filter((x) => x.id !== m.id));
                onRevokeInvite?.(m.id);
                setAnnouncement(`${name} 초대를 취소했습니다.`);
              }}
            >
              초대 취소
            </Button>
          );
        }
        return (
          <Button
            variant="ghost"
            size="sm"
            aria-label={`${name} 다시 활성화`}
            onClick={() => {
              update(m.id, { status: "ACTIVE" });
              onReactivate?.(m.id);
              setAnnouncement(`${name}님을 다시 활성화했습니다.`);
            }}
          >
            다시 활성화
          </Button>
        );
      },
    },
  ];

  const filters: { key: StatusFilter; label: string }[] = [
    { key: "ALL", label: "전체" },
    { key: "ACTIVE", label: "활성" },
    { key: "INVITED", label: "초대됨" },
    { key: "INACTIVE", label: "비활성" },
  ];
  const loading = state === "loading";

  return (
    <>
      <PageHeader
        title="멤버"
        description="직원을 초대하고 역할과 상태를 관리합니다. 역할에 따라 볼 수 있는 메뉴가 달라집니다."
        actions={demo ? <Badge tone="neutral">합성 데이터</Badge> : undefined}
      />

      <p className="sr-only" role="status" aria-live="polite">
        {announcement}
      </p>

      <div className="grid gap-6">
        {notice}
        <Panel>
          <PanelHeader
            title="멤버 초대"
            description="초대 메일의 링크로 가입하면 활성 멤버가 됩니다."
          />
          <form noValidate onSubmit={submitInvite}>
            <PanelBody className="grid items-start gap-4 md:grid-cols-[minmax(0,1fr)_15rem]">
              <Field
                label="이메일"
                hint={`쉼표나 줄바꿈으로 나눠 여러 명을 한 번에 초대할 수 있습니다. 최대 ${MAX_INVITES}명. 초대한 주소를 다시 넣으면 안내 메일을 다시 보냅니다.`}
                error={touched ? emailError : undefined}
                className="md:row-span-2"
              >
                <Textarea
                  ref={emailsRef}
                  name="emails"
                  rows={4}
                  value={emails}
                  placeholder="name@acme.test"
                  autoComplete="off"
                  spellCheck={false}
                  onChange={(e) => {
                    setEmails(e.target.value);
                    setInviteResult(null);
                    if (touched) setEmailError(validateEmails(e.target.value, rows));
                  }}
                  onBlur={() => {
                    // First blur turns validation on, but an untouched empty box is not an error yet.
                    if (!touched && !emails.trim()) return;
                    setTouched(true);
                    setEmailError(validateEmails(emails, rows));
                  }}
                />
              </Field>
              <Field label="역할" hint="나중에 표에서 바꿀 수 있습니다.">
                <Select name="role" value={role} onChange={(e) => setRole(e.target.value as MemberRole)}>
                  {(Object.keys(roleLabel) as MemberRole[]).map((r) => (
                    <option key={r} value={r}>
                      {roleLabel[r]}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="부서" optional>
                <Select name="departmentId" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
                  <option value="">지정 안 함</option>
                  {departments.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
                </Select>
              </Field>
            </PanelBody>
            <PanelFooter className="flex-wrap justify-between">
              <div className="min-w-0 flex-1">
                {inviteResult ? (
                  <Notice tone="success" title={inviteResult} className="py-2" />
                ) : null}
              </div>
              <Button type="submit" loading={inviting}>
                <Send aria-hidden="true" />
                초대 보내기
              </Button>
            </PanelFooter>
          </form>
        </Panel>

        <section aria-labelledby="members-title" className="grid content-start gap-3">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <h2 id="members-title" className="text-title-sm font-semibold text-ink">
              멤버 목록
            </h2>
            <div role="group" aria-label="상태로 거르기" className="inline-flex rounded-control bg-surface-sunken p-0.5">
              {filters.map((f) => {
                const pressed = filter === f.key;
                return (
                  <button
                    key={f.key}
                    type="button"
                    aria-pressed={pressed}
                    onClick={() => setFilter(f.key)}
                    className={cn(
                      "inline-flex h-8 items-center gap-1.5 rounded-[5px] px-3 text-label font-medium transition-colors duration-150",
                      pressed ? "bg-surface text-ink shadow-press" : "text-ink-2 hover:text-ink",
                    )}
                  >
                    {f.label}
                    {loading ? null : <span className="text-muted tabular">{counts[f.key]}</span>}
                  </button>
                );
              })}
            </div>
          </div>

          {self ? (
            <p id="members-self-note" className="text-label text-muted">
              본인 계정({self.name})은 여기에서 비활성화하거나 역할을 바꿀 수 없습니다. 다른 조직 관리자에게 요청하세요.
            </p>
          ) : null}

          <DataTable
            caption="멤버 목록"
            columns={columns}
            rows={visible}
            rowKey={(m) => m.id}
            loading={loading}
            minWidth="min-w-[40rem]"
            error={
              state === "error"
                ? {
                    title: "멤버 목록을 불러오지 못했습니다",
                    description: "서버와 연결이 잠시 끊겼습니다. 위 초대 칸에 입력한 내용은 그대로 있습니다.",
                    traceId: "7d3e1c9a52b04f6e8a1b2c3d4e5f6a7b",
                    onRetry: () => undefined,
                  }
                : undefined
            }
            empty={{
              title: `${filters.find((f) => f.key === filter)?.label} 멤버가 없습니다`,
              description: "다른 상태를 고르거나 전체 목록을 보세요.",
              action: (
                <Button variant="secondary" size="sm" onClick={() => setFilter("ALL")}>
                  전체 보기
                </Button>
              ),
            }}
          />

          {onlySelf ? (
            <EmptyState
              icon={UserRoundPlus}
              title="아직 초대한 멤버가 없습니다"
              description={`지금은 ${self?.name ?? "본인"}님만 이 회사에 있습니다. 직원 이메일을 초대 칸에 붙여 넣으면 한 번에 여러 명을 초대할 수 있습니다.`}
              action={
                <Button variant="secondary" onClick={() => emailsRef.current?.focus()}>
                  이메일 입력하기
                </Button>
              }
            />
          ) : null}
        </section>
      </div>
    </>
  );
}

/** Full-page composition with the demo shell, for Storybook. */
function AdminMembersScreen(props: AdminMembersScreenProps) {
  return (
    <AppShell
      workspace={<WorkspaceSwitcher name="에이크미" />}
      account={<AccountArea name="김하나" role="조직 관리자" />}
      sections={withCurrent(adminNav, "/app/admin/members")}
    >
      <AdminMembers {...props} demo />
    </AppShell>
  );
}

export { AdminMembers, AdminMembersScreen, type AdminMembersProps, type AdminMembersScreenProps, type InviteInput };
