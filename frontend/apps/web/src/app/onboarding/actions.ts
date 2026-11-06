"use server";

import { unstable_rethrow } from "next/navigation";

import { loginHref } from "@/lib/return-to";
import { createTenantSchema, type CreateTenantInput } from "@/lib/tenant-schema";
import { ApiError, UnauthenticatedError, apiFetch } from "@/server/api";
import type { CreateTenantRequest, CreateTenantResponse } from "@/server/api-types";

export type CreateTenantResult =
  | { ok: true; redirectTo: string }
  | {
      ok: false;
      message: string;
      fieldErrors?: Partial<Record<keyof CreateTenantInput, string>>;
      traceId?: string;
    };

const ERROR_CODE_MESSAGES: Record<string, { message: string; field?: keyof CreateTenantInput }> = {
  TENANT_SLUG_TAKEN: { message: "이미 사용 중인 회사 코드입니다", field: "slug" },
};

/**
 * Server action: POST /v1/tenants as the current user.
 * The client already validated with the same zod schema; this is the authoritative check.
 */
export async function createTenant(input: unknown): Promise<CreateTenantResult> {
  const parsed = createTenantSchema.safeParse(input);
  if (!parsed.success) {
    const fieldErrors: Partial<Record<keyof CreateTenantInput, string>> = {};
    for (const issue of parsed.error.issues) {
      const field = issue.path[0];
      if (typeof field === "string" && field in createTenantSchema.shape) {
        fieldErrors[field as keyof CreateTenantInput] ??= issue.message;
      }
    }
    return { ok: false, message: "입력값을 확인해 주세요.", fieldErrors };
  }

  const body: CreateTenantRequest = parsed.data;
  try {
    const tenant = await apiFetch<CreateTenantResponse>("/v1/tenants", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    // Re-login with organization:<slug> so the new token carries the new organization.
    return { ok: true, redirectTo: loginHref({ org: tenant.slug, returnTo: "/app" }) };
  } catch (error) {
    unstable_rethrow(error);
    if (error instanceof UnauthenticatedError) {
      return { ok: false, message: "로그인이 만료되었습니다. 다시 로그인한 뒤 시도해 주세요." };
    }
    if (error instanceof ApiError) {
      const known = error.code ? ERROR_CODE_MESSAGES[error.code] : undefined;
      if (known) {
        return {
          ok: false,
          message: known.message,
          fieldErrors: known.field ? { [known.field]: known.message } : undefined,
          traceId: error.traceId,
        };
      }
      return {
        ok: false,
        message: error.problem.detail ?? error.problem.title ?? "회사를 등록하지 못했습니다.",
        traceId: error.traceId,
      };
    }
    console.error("[onboarding] POST /v1/tenants failed:", error);
    return { ok: false, message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." };
  }
}
