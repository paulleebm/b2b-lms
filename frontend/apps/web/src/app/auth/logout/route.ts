import { NextResponse, type NextRequest } from "next/server";
import * as oidc from "openid-client";

import { getEnv } from "@/server/env";
import { getOidcConfig, postLogoutRedirectUri } from "@/server/oidc";
import {
  SESSION_COOKIE,
  deleteSession,
  isValidSessionId,
  loadSession,
  sessionCookieOptions,
} from "@/server/session";

export const dynamic = "force-dynamic";

/**
 * POST /auth/logout (form submit). POST-only so a cross-site <img> can't log users out;
 * SameSite=Lax already withholds the cookie on cross-site POSTs, the Origin check is
 * defence in depth.
 */
export async function POST(request: NextRequest) {
  const env = getEnv();
  const origin = request.headers.get("origin");
  if (origin && origin !== new URL(env.APP_URL).origin) {
    return new NextResponse("Forbidden", { status: 403 });
  }

  const sessionId = request.cookies.get(SESSION_COOKIE)?.value;
  let idToken: string | undefined;
  if (isValidSessionId(sessionId)) {
    idToken = (await loadSession(sessionId))?.idToken;
    await deleteSession(sessionId);
  }

  // RP-initiated logout ends the Keycloak SSO session too. Fall back to the landing page
  // if we have no id_token or Keycloak is unreachable — the local session is gone either way.
  let target = new URL("/", env.APP_URL);
  if (idToken) {
    try {
      target = oidc.buildEndSessionUrl(await getOidcConfig(), {
        id_token_hint: idToken,
        post_logout_redirect_uri: postLogoutRedirectUri(),
      });
    } catch (error) {
      console.error("[auth/logout] could not build end-session URL:", error);
    }
  }

  // 303 so the browser follows with GET.
  const response = NextResponse.redirect(target, 303);
  response.headers.set("Cache-Control", "no-store");
  response.cookies.set(SESSION_COOKIE, "", { ...sessionCookieOptions, maxAge: 0 });
  return response;
}
