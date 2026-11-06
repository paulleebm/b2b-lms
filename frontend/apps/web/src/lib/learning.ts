import type { MyEnrollment } from "@lms/api-client";
import type { EnrollmentStatus, LearnerCourse } from "@lms/ui";

const SEOUL = "Asia/Seoul";
const DAY_MS = 24 * 60 * 60 * 1000;
/** "마감 임박" window, in days. */
const DUE_SOON_DAYS = 3;

/** ISO instant → "YYYY-MM-DD" in Korea time (what the design system's date helpers take). */
export function seoulDate(iso: string): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: SEOUL, year: "numeric", month: "2-digit", day: "2-digit" })
    .formatToParts(new Date(iso));
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "";
  return `${get("year")}-${get("month")}-${get("day")}`;
}

/** ISO instant → "2026. 11. 6." */
export function koreanDate(iso: string): string {
  return new Intl.DateTimeFormat("ko-KR", { timeZone: SEOUL, year: "numeric", month: "numeric", day: "numeric" }).format(
    new Date(iso),
  );
}

/**
 * The badge a learner sees. "Overdue" comes from the API (computed there, never stored);
 * "due soon" is a display rule: three days or fewer.
 */
export function enrollmentStatus(e: Pick<MyEnrollment, "status" | "overdue" | "dueAt" | "completedAt" | "lessonsDone" | "lessonsTotal">, now = Date.now()): EnrollmentStatus {
  if (e.status === "COMPLETED") return { kind: "completed", completedAt: e.completedAt ? koreanDate(e.completedAt) : undefined };
  if (e.overdue) return { kind: "overdue" };
  if (e.dueAt) {
    const daysLeft = Math.ceil((new Date(e.dueAt).getTime() - now) / DAY_MS);
    if (daysLeft <= DUE_SOON_DAYS) return { kind: "due-soon", daysLeft: Math.max(0, daysLeft) };
  }
  if (e.status === "IN_PROGRESS") {
    return { kind: "in-progress", percent: e.lessonsTotal ? Math.round((e.lessonsDone / e.lessonsTotal) * 100) : 0 };
  }
  return { kind: "not-started" };
}

export function toLearnerCourse(e: MyEnrollment): LearnerCourse {
  return {
    id: e.id,
    title: e.title,
    status: enrollmentStatus(e),
    dueAt: e.dueAt ? seoulDate(e.dueAt) : "",
    lessonsDone: e.lessonsDone,
    lessonsTotal: e.lessonsTotal,
    nextLesson: e.nextLesson?.title,
    completedAt: e.completedAt ? koreanDate(e.completedAt) : undefined,
    href: `/app/learn/${e.id}`,
    certificateSerial: e.certificateSerial ?? undefined,
    certificateHref: e.certificateSerial ? `/app/certificates/${e.id}` : undefined,
  };
}

/** "754" → "12:34" */
export function clock(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = String(s % 60).padStart(2, "0");
  return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${sec}` : `${m}:${sec}`;
}
