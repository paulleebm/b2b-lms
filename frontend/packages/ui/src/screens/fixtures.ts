/**
 * SYNTHETIC demo data for Storybook and screen reviews. Not real customers or results
 * (PRODUCT.md "Evidence on Hand"): screens using it display a "합성 데이터" badge.
 */
import type { BarDatum } from "../components/bar-chart";
import type { EnrollmentStatus } from "../components/status-badge";

export type LearnerRow = {
  id: string;
  name: string;
  department: string;
  course: string;
  status: EnrollmentStatus;
  /** ISO date */
  dueAt: string;
  progress: number;
};

export const departmentCompletion: BarDatum[] = [
  { label: "인사팀", value: 92, overdue: 0, detail: "11/12" },
  { label: "개발팀", value: 78, overdue: 6, detail: "14/18" },
  { label: "재무팀", value: 70, overdue: 10, detail: "7/10" },
  { label: "영업팀", value: 58, overdue: 17, detail: "7/12" },
  { label: "고객지원팀", value: 50, overdue: 8, detail: "6/12" },
];

export const pendingLearners: LearnerRow[] = [
  { id: "1", name: "박서준", department: "영업팀", course: "개인정보보호 교육", status: { kind: "overdue" }, dueAt: "2026-11-03", progress: 20 },
  { id: "2", name: "최유나", department: "고객지원팀", course: "직장 내 괴롭힘 예방", status: { kind: "overdue" }, dueAt: "2026-11-04", progress: 0 },
  { id: "3", name: "정민호", department: "재무팀", course: "정보보안 기초", status: { kind: "due-soon", daysLeft: 1 }, dueAt: "2026-11-07", progress: 60 },
  { id: "4", name: "한지민", department: "개발팀", course: "정보보안 기초", status: { kind: "due-soon", daysLeft: 2 }, dueAt: "2026-11-08", progress: 85 },
  { id: "5", name: "오세훈", department: "영업팀", course: "산업안전보건 교육", status: { kind: "in-progress", percent: 40 }, dueAt: "2026-11-20", progress: 40 },
  { id: "6", name: "윤하은", department: "고객지원팀", course: "개인정보보호 교육", status: { kind: "not-started" }, dueAt: "2026-11-30", progress: 0 },
];

/** Edge case: long names, long department, many rows (craft/state-coverage "Edge"). */
export const edgeLearners: LearnerRow[] = Array.from({ length: 40 }, (_, i) => ({
  id: `e${i}`,
  name: i === 0 ? "알렉산드라 크리스티나 몬테네그로 가르시아" : `학습자 ${i + 1}`,
  department: i === 0 ? "글로벌 전략기획본부 디지털트랜스포메이션 추진단" : ["개발팀", "영업팀", "재무팀"][i % 3]!,
  course: i === 1 ? "2026년 하반기 전사 필수 정보보안 및 개인정보보호 통합 교육 (재이수자 대상)" : "정보보안 기초",
  status: i % 4 === 0 ? { kind: "overdue" } : { kind: "in-progress", percent: (i * 7) % 100 },
  dueAt: "2026-11-30",
  progress: (i * 7) % 100,
}));

export type LearnerCourse = {
  id: string;
  title: string;
  status: EnrollmentStatus;
  dueAt: string;
  lessonsDone: number;
  lessonsTotal: number;
  nextLesson?: string;
  completedAt?: string;
};

export const learnerCourses: LearnerCourse[] = [
  { id: "c1", title: "정보보안 기초", status: { kind: "due-soon", daysLeft: 2 }, dueAt: "2026-11-08", lessonsDone: 4, lessonsTotal: 6, nextLesson: "5. 피싱 메일 구별하기" },
  { id: "c2", title: "산업안전보건 교육", status: { kind: "in-progress", percent: 25 }, dueAt: "2026-11-20", lessonsDone: 2, lessonsTotal: 8, nextLesson: "3. 사무실 안전 수칙" },
  { id: "c3", title: "직장 내 괴롭힘 예방", status: { kind: "not-started" }, dueAt: "2026-11-30", lessonsDone: 0, lessonsTotal: 5, nextLesson: "1. 괴롭힘의 정의" },
];

export const completedCourses: LearnerCourse[] = [
  { id: "d1", title: "개인정보보호 교육", status: { kind: "completed", completedAt: "2026. 10. 28." }, dueAt: "2026-10-31", lessonsDone: 6, lessonsTotal: 6, completedAt: "2026. 10. 28." },
  { id: "d2", title: "성희롱 예방 교육", status: { kind: "completed", completedAt: "2026. 9. 15." }, dueAt: "2026-09-30", lessonsDone: 4, lessonsTotal: 4, completedAt: "2026. 9. 15." },
];

/** "2026-11-08" → "11. 8." — compact form for table cells. */
export function formatDueShort(iso: string): string {
  const [, m, d] = iso.split("-");
  return `${Number(m)}. ${Number(d)}.`;
}

/** "2026-11-08" → "11월 8일 (일)" */
export function formatDue(iso: string): string {
  const d = new Date(`${iso}T00:00:00+09:00`);
  return new Intl.DateTimeFormat("ko-KR", { month: "long", day: "numeric", weekday: "short", timeZone: "Asia/Seoul" }).format(d);
}
