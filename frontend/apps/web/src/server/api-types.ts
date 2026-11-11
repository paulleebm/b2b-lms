// Types come from the Spring API's OpenAPI contract (packages/api-client, generated).
// Only request shapes the API does not name for us are declared here.
export type {
  CourseDetail,
  CourseSummary,
  MeResponse,
  MemberView,
  ProblemDetails,
  Role,
} from "@lms/api-client";

export type MembershipStatus = "ACTIVE" | "INVITED" | "DISABLED";

/** POST /v1/tenants request body */
export interface CreateTenantRequest {
  name: string;
  slug: string;
  domain: string;
}

/** POST /v1/tenants → 201 */
export interface CreateTenantResponse {
  id: string;
  slug: string;
  name: string;
}
