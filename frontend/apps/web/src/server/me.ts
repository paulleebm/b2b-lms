import "server-only";

import { unstable_rethrow } from "next/navigation";
import { cache } from "react";

import { ApiError, UnauthenticatedError, apiFetch } from "./api";
import type { MeResponse } from "./api-types";

export type MeResult =
  | { ok: true; me: MeResponse }
  | { ok: false; title: string; detail?: string; traceId?: string }
  | "unauthenticated";

/** GET /v1/me once per request: the /app layout and its pages share the result. */
export const loadMe = cache(async (): Promise<MeResult> => {
  try {
    return { ok: true, me: await apiFetch<MeResponse>("/v1/me") };
  } catch (error) {
    // Let Next.js control-flow errors (dynamic usage, redirect, notFound) propagate.
    unstable_rethrow(error);
    if (error instanceof UnauthenticatedError) return "unauthenticated";
    if (error instanceof ApiError) {
      // A 401 here means our session exists but the API rejects the token. Show an error
      // instead of redirecting, otherwise Keycloak SSO would loop us straight back.
      const title =
        error.status === 401
          ? "인증 정보를 확인하지 못했습니다"
          : error.status === 403
            ? "이 회사에 접근할 권한이 없습니다"
            : "내 정보를 불러오지 못했습니다";
      return { ok: false, title, detail: error.problem.detail, traceId: error.traceId };
    }
    console.error("[app] GET /v1/me failed:", error);
    return { ok: false, title: "API 서버에 연결할 수 없습니다", detail: "잠시 후 다시 시도해 주세요." };
  }
});

/** For pages under /app: the layout already handled every non-ok case. */
export async function requireMe(): Promise<MeResponse> {
  const result = await loadMe();
  if (result === "unauthenticated" || !result.ok) throw new Error("requireMe outside the /app layout");
  return result.me;
}
