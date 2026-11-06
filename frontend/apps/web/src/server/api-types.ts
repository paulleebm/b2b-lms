// Hand-written Day 1 contract with the Spring API.
// Day 2: replace with types generated from springdoc-openapi (docs/02 B12).

export type Role = "ORG_ADMIN" | "INSTRUCTOR" | "LEARNER";

export type MembershipStatus = "ACTIVE" | "INVITED" | "DISABLED";

export interface TenantSummary {
  id: string;
  slug: string;
  name: string;
}

export interface Membership {
  tenantId: string;
  slug: string;
  name: string;
  role: Role;
  /** Typed as string as well so an unknown status from the API doesn't break rendering. */
  status: MembershipStatus | (string & {});
}

/** GET /v1/me */
export interface MeResponse {
  userId: string;
  email: string;
  name: string | null;
  currentTenant: TenantSummary | null;
  role: Role | null;
  memberships: Membership[];
}

/** POST /v1/tenants request body */
export interface CreateTenantRequest {
  name: string;
  slug: string;
  domain: string;
}

/** POST /v1/tenants → 201 */
export type CreateTenantResponse = TenantSummary;

/** RFC 9457 Problem Details plus our extension members. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Stable machine-readable error code, e.g. TENANT_SLUG_TAKEN. */
  code?: string;
  traceId?: string;
}
