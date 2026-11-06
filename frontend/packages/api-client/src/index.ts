// Friendly names for the generated contract. Regenerate after an API change:
//   (backend)  ./gradlew test --tests com.lms.OpenApiContractIT   → openapi.json
//   (frontend) pnpm --filter @lms/api-client generate            → src/schema.d.ts
import type { components, operations, paths } from "./schema";

export type { components, operations, paths };

type S = components["schemas"];

export type MeResponse = S["MeResponse"];
export type MemberView = S["MemberView"];
export type Role = MemberView["role"];
export type MemberStatus = MemberView["status"];
export type InviteRequest = S["InviteRequest"];
export type InviteResult = S["InviteResult"];
export type UpdateMemberRequest = S["UpdateMemberRequest"];
export type Department = S["DepartmentResponse"];

export type CourseSummary = S["CourseSummary"];
export type CourseDetail = S["CourseDetail"];
export type SectionView = S["SectionView"];
export type LessonView = S["LessonView"];
export type LessonType = LessonView["type"];
export type ContentState = LessonView["contentState"];
export type OutlineRequest = S["OutlineRequest"];
export type UpdateLessonRequest = S["UpdateLessonRequest"];

export type InitUploadRequest = S["InitUploadRequest"];
export type InitUploadResult = S["InitResult"];
export type CompleteUploadRequest = S["CompleteUploadRequest"];
export type CompleteUploadResult = S["CompleteResult"];
export type PartUrl = S["PartUrl"];
export type VideoResponse = S["VideoResponse"];

/** RFC 9457 problem+json with our extension members. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Stable machine-readable code, e.g. COURSE_CHANGED. */
  code?: string;
  traceId?: string;
  /** Field errors (VALIDATION_FAILED) or publish problems (COURSE_NOT_READY). */
  errors?: Array<Record<string, unknown>>;
}
