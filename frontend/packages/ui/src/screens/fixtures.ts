/**
 * SYNTHETIC demo data for Storybook and screen reviews. Not real customers or results
 * (PRODUCT.md "Evidence on Hand"): screens using it display a "합성 데이터" badge.
 */
import type { BarDatum } from "../components/bar-chart";
import type { OutlineSection } from "../components/course-outline";
import type { UploadItem } from "../components/file-upload";
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

/* ---------------------------------------------------------------------------------------------
 * Members (admin "멤버")
 * ------------------------------------------------------------------------------------------- */

export type MemberRole = "ORG_ADMIN" | "INSTRUCTOR" | "LEARNER";
export type MemberStatus = "ACTIVE" | "INVITED" | "INACTIVE";

export type Department = { id: string; name: string };

export type MemberRow = {
  id: string;
  name: string;
  email: string;
  /** Missing on purpose for some rows: department is optional (craft/state-coverage "빠진 선택 항목"). */
  departmentId?: string;
  role: MemberRole;
  status: MemberStatus;
  /** The signed-in admin: cannot disable themselves or change their own role. */
  isSelf?: boolean;
};

export const roleLabel: Record<MemberRole, string> = {
  ORG_ADMIN: "조직 관리자",
  INSTRUCTOR: "강사",
  LEARNER: "학습자",
};

export const departments: Department[] = [
  { id: "d-hr", name: "인사팀" },
  { id: "d-dev", name: "개발팀" },
  { id: "d-fin", name: "재무팀" },
  { id: "d-sales", name: "영업팀" },
  { id: "d-cs", name: "고객지원팀" },
];

export const selfMember: MemberRow = {
  id: "m-self",
  name: "김하나",
  email: "hana.kim@acme.test",
  departmentId: "d-hr",
  role: "ORG_ADMIN",
  status: "ACTIVE",
  isSelf: true,
};

export const members: MemberRow[] = [
  selfMember,
  { id: "m2", name: "이도윤", email: "doyun.lee@acme.test", departmentId: "d-hr", role: "INSTRUCTOR", status: "ACTIVE" },
  { id: "m3", name: "박서준", email: "seojun.park@acme.test", departmentId: "d-sales", role: "LEARNER", status: "ACTIVE" },
  { id: "m4", name: "최유나", email: "yuna.choi@acme.test", departmentId: "d-cs", role: "LEARNER", status: "ACTIVE" },
  { id: "m5", name: "정민호", email: "minho.jung@acme.test", departmentId: "d-fin", role: "LEARNER", status: "INVITED" },
  { id: "m6", name: "한지민", email: "jimin.han@acme.test", departmentId: "d-dev", role: "INSTRUCTOR", status: "ACTIVE" },
  { id: "m7", name: "오세훈", email: "sehun.oh@acme.test", role: "LEARNER", status: "INVITED" },
  { id: "m8", name: "윤하은", email: "haeun.yoon@acme.test", departmentId: "d-cs", role: "LEARNER", status: "INACTIVE" },
];

/** Edge: 40 members, a 40-character name, a very long email, a long department name. */
export const edgeDepartments: Department[] = [
  ...departments,
  { id: "d-long", name: "글로벌 전략기획본부 디지털트랜스포메이션 추진단" },
];

export const edgeMembers: MemberRow[] = [
  selfMember,
  ...Array.from({ length: 39 }, (_, i): MemberRow => {
    const n = i + 2;
    const statuses: MemberStatus[] = ["ACTIVE", "ACTIVE", "INVITED", "ACTIVE", "INACTIVE"];
    return {
      id: `em${n}`,
      name: i === 0 ? "알렉산드라 크리스티나 몬테네그로 가르시아 페르난데스 로드리게스" : `구성원 ${n}`,
      email:
        i === 0
          ? "alexandra.cristina.montenegro.garcia.fernandez@global-strategy.acme.test"
          : `member${String(n).padStart(2, "0")}@acme.test`,
      departmentId: i === 0 ? "d-long" : i % 6 === 5 ? undefined : departments[i % departments.length]!.id,
      role: i % 9 === 0 ? "INSTRUCTOR" : "LEARNER",
      status: statuses[i % statuses.length]!,
    };
  }),
];

/* ---------------------------------------------------------------------------------------------
 * Course editor (instructor "코스 편집")
 * ------------------------------------------------------------------------------------------- */

export type CourseDraft = {
  id: string;
  title: string;
  description: string;
  status: "DRAFT" | "PUBLISHED";
  sections: OutlineSection[];
  /** Upload row per VIDEO / DOCUMENT lesson id. */
  files: Record<string, UploadItem>;
  /** Body per TEXT lesson id. */
  texts: Record<string, string>;
};

const MB = 1024 * 1024;

/** Everything uploaded and transcoded: ready to publish. */
export const courseReady: CourseDraft = {
  id: "course-sec-101",
  title: "정보보안 기초",
  description: "회사 계정과 고객 정보를 지키기 위해 모든 직원이 알아야 할 기본 수칙을 다룹니다.",
  status: "DRAFT",
  sections: [
    {
      id: "s1",
      title: "보안의 기본",
      lessons: [
        { id: "l1", title: "왜 정보보안인가", type: "VIDEO", status: "ready" },
        { id: "l2", title: "회사 보안 정책 요약", type: "DOCUMENT", status: "ready" },
        { id: "l3", title: "비밀번호 관리 수칙", type: "TEXT" },
      ],
    },
    {
      id: "s2",
      title: "실전 사례",
      lessons: [
        { id: "l4", title: "피싱 메일 구별하기", type: "VIDEO", status: "ready" },
        { id: "l5", title: "보안 서약서", type: "DOCUMENT", status: "ready" },
        { id: "l6", title: "사고 신고 절차", type: "TEXT" },
      ],
    },
  ],
  files: {
    l1: { id: "f1", name: "01_왜_정보보안인가.mp4", sizeBytes: 486 * MB, kind: "video", state: "ready" },
    l2: { id: "f2", name: "보안정책_요약_v3.pdf", sizeBytes: 2.4 * MB, kind: "document", state: "ready" },
    l4: { id: "f4", name: "02_피싱메일_구별하기.mp4", sizeBytes: 1.3 * 1024 * MB, kind: "video", state: "ready" },
    l5: { id: "f5", name: "보안서약서_2026.pdf", sizeBytes: 0.8 * MB, kind: "document", state: "ready" },
  },
  texts: {
    l3: "비밀번호는 12자 이상으로 만들고, 다른 서비스와 같은 비밀번호를 쓰지 않습니다.\n\n회사 계정에는 2단계 인증을 켭니다.",
    l6: "보안 사고가 의심되면 바로 보안 담당자에게 알립니다. 스스로 해결하려고 기다리지 않습니다.",
  },
};

/** One video still transcoding and one document lesson without a file: publish is blocked. */
export const courseBlocked: CourseDraft = {
  ...courseReady,
  sections: courseReady.sections.map((s) =>
    s.id !== "s2"
      ? s
      : {
          ...s,
          lessons: s.lessons.map((l) =>
            l.id === "l4" ? { ...l, status: "processing" as const } : l.id === "l5" ? { ...l, status: "missing-file" as const } : l,
          ),
        },
  ),
  files: {
    l1: courseReady.files.l1!,
    l2: courseReady.files.l2!,
    l4: { ...courseReady.files.l4!, state: "processing" },
  },
};

/** A course created a minute ago: title only, no sections yet. */
export const courseNew: CourseDraft = {
  id: "course-onboarding",
  title: "신입사원 온보딩",
  description: "",
  status: "DRAFT",
  sections: [],
  files: {},
  texts: {},
};

/** Edge: an 80-character title, a section of 12 lessons, long lesson titles, a failed transcode. */
export const courseEdge: CourseDraft = {
  id: "course-edge",
  title: "2026년 하반기 전사 필수 정보보안 및 개인정보보호 통합 교육 (재이수자 대상, 협력사 상주 인력 포함)",
  description:
    "지난 교육 이후 바뀐 사내 보안 규정과 실제 사고 사례를 중심으로 다시 구성했습니다. 협력사 상주 인력도 같은 기준으로 이수해야 합니다.",
  status: "PUBLISHED",
  sections: [
    {
      id: "es1",
      title: "왜 다시 듣게 되었나요: 지난 교육 이후 바뀐 사내 보안 규정과 2026년 상반기 사고 사례 정리",
      lessons: Array.from({ length: 12 }, (_, i) => ({
        id: `el${i + 1}`,
        title:
          i === 0
            ? "개인정보 처리방침 개정 사항 전체 요약과 부서별로 달라지는 업무 절차 및 체크리스트"
            : `사례 ${i + 1}. 실제 사고 재구성`,
        type: (["VIDEO", "DOCUMENT", "TEXT"] as const)[i % 3]!,
        status: i === 3 ? ("failed" as const) : i % 3 === 2 ? undefined : ("ready" as const),
      })),
    },
    { id: "es2", title: "부록", lessons: [] },
  ],
  files: {
    ...Object.fromEntries(
      Array.from({ length: 12 }, (_, i) => i)
        .filter((i) => i % 3 !== 2)
        .map((i): [string, UploadItem] => [
          `el${i + 1}`,
          i % 3 === 0
            ? { id: `ef${i + 1}`, name: `사례${i + 1}.mp4`, sizeBytes: (300 + i * 40) * MB, kind: "video", state: "ready" }
            : { id: `ef${i + 1}`, name: `사례${i + 1}_자료.pdf`, sizeBytes: (1 + i) * MB, kind: "document", state: "ready" },
        ]),
    ),
    el1: {
      id: "ef1",
      name: "2026_하반기_전사필수_정보보안_개인정보보호_통합교육_재이수자대상_최종_수정본_v12_진짜최종.mp4",
      sizeBytes: 4.8 * 1024 * MB,
      kind: "video",
      state: "ready",
    },
    el4: {
      id: "ef4",
      name: "사례4_원본.mp4",
      sizeBytes: 912 * MB,
      kind: "video",
      state: "failed",
      error: "영상을 읽지 못했습니다. 지원하지 않는 코덱이거나 손상된 파일입니다. 영상 파일을 확인하고 다시 올려 주세요.",
    },
  },
  texts: {},
};

/** S3 multipart part size used by the upload flow (docs/05 §6): 10 MB. */
export const UPLOAD_PART_BYTES = 10 * MB;
