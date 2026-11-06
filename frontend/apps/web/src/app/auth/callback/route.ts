import { NextResponse, type NextRequest } from "next/server";
import * as oidc from "openid-client";
import { z } from "zod";

import { safeReturnTo } from "@/lib/return-to";
import { getEnv } from "@/server/env";
import { getOidcConfig } from "@/server/oidc";
import { redis } from "@/server/redis";
import {
  SESSION_COOKIE,
  deleteSession,
  isValidSessionId,
  newSessionId,
  saveSession,
  sessionCookieOptions,
  sessionFromTokens,
} from "@/server/session";

export const dynamic = "force-dynamic";

const pendingLoginSchema = z.object({
  codeVerifier: z.string(),
  nonce: z.string(),
  returnTo: z.string(),
});

/**
 * GET /auth/callback?code=...&state=...
 * Exchanges the code (PKCE + client secret), validates the ID token (signature, iss, aud,
 * exp, nonce — done by openid-client) and creates a fresh server-side session.
 */
export async function GET(request: NextRequest) {
  const env = getEnv();
  const state = request.nextUrl.searchParams.get("state");
  if (!state) return badRequest();

  // One-time use: GETDEL so a replayed callback (or a second tab) cannot reuse the verifier.
  const raw = await redis().getdel(`login:${state}`);
  if (!raw) return badRequest();

  const pending = pendingLoginSchema.safeParse(JSON.parse(raw));
  if (!pending.success) return badRequest();
  const { codeVerifier, nonce, returnTo } = pending.data;

  let tokens: Awaited<ReturnType<typeof oidc.authorizationCodeGrant>>;
  try {
    const config = await getOidcConfig();
    // Rebuild the URL from APP_URL: behind a proxy request.url may not match the
    // registered redirect URI, and openid-client derives redirect_uri from this URL.
    const currentUrl = new URL(`/auth/callback${request.nextUrl.search}`, env.APP_URL);
    tokens = await oidc.authorizationCodeGrant(config, currentUrl, {
      pkceCodeVerifier: codeVerifier,
      expectedState: state,
      expectedNonce: nonce,
      idTokenExpected: true,
    });
  } catch (error) {
    console.error("[auth/callback] code exchange failed:", describeError(error));
    return redirectTo(`/?error=login_failed`, env.APP_URL);
  }

  // Session fixation defence: always mint a new id and drop any session the browser had.
  const previousId = request.cookies.get(SESSION_COOKIE)?.value;
  if (isValidSessionId(previousId)) await deleteSession(previousId);

  const sessionId = newSessionId();
  const { session, ttlSeconds } = sessionFromTokens(tokens);
  await saveSession(sessionId, session, ttlSeconds);

  const response = redirectTo(safeReturnTo(returnTo), env.APP_URL);
  response.cookies.set(SESSION_COOKIE, sessionId, sessionCookieOptions);
  return response;
}

function redirectTo(path: string, appUrl: string) {
  const response = NextResponse.redirect(new URL(path, appUrl), 302);
  response.headers.set("Cache-Control", "no-store");
  return response;
}

function badRequest() {
  return new NextResponse("유효하지 않거나 만료된 로그인 요청입니다. 처음부터 다시 로그인해 주세요.", {
    status: 400,
    headers: { "Content-Type": "text/plain; charset=utf-8", "Cache-Control": "no-store" },
  });
}

function describeError(error: unknown): string {
  if (error instanceof oidc.AuthorizationResponseError || error instanceof oidc.ResponseBodyError) {
    return `${error.error}: ${error.error_description ?? ""}`;
  }
  return error instanceof Error ? error.message : String(error);
}
