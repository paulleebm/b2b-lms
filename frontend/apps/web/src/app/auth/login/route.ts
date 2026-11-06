import { NextResponse, type NextRequest } from "next/server";
import * as oidc from "openid-client";

import { safeReturnTo } from "@/lib/return-to";
import { ORG_ALIAS_RE, getOidcConfig, redirectUri, scopeFor } from "@/server/oidc";
import { redis } from "@/server/redis";

export const dynamic = "force-dynamic";

const LOGIN_STATE_TTL_S = 300;

/**
 * GET /auth/login[?org=<alias>][&returnTo=/path]
 * Starts Authorization Code + PKCE. The verifier and nonce stay in Redis (5 min, one-time),
 * only the S256 challenge and opaque state go to the browser.
 */
export async function GET(request: NextRequest) {
  const params = request.nextUrl.searchParams;
  const org = params.get("org") ?? undefined;
  if (org !== undefined && !ORG_ALIAS_RE.test(org)) {
    return new NextResponse("잘못된 조직 코드입니다.", { status: 400 });
  }
  const returnTo = safeReturnTo(params.get("returnTo"));

  const config = await getOidcConfig();
  const codeVerifier = oidc.randomPKCECodeVerifier();
  const codeChallenge = await oidc.calculatePKCECodeChallenge(codeVerifier);
  const state = oidc.randomState();
  const nonce = oidc.randomNonce();

  await redis().set(
    `login:${state}`,
    JSON.stringify({ codeVerifier, nonce, returnTo }),
    "EX",
    LOGIN_STATE_TTL_S,
  );

  const authorizationUrl = oidc.buildAuthorizationUrl(config, {
    redirect_uri: redirectUri(),
    response_type: "code",
    scope: scopeFor(org),
    code_challenge: codeChallenge,
    code_challenge_method: "S256",
    state,
    nonce,
  });

  const response = NextResponse.redirect(authorizationUrl, 302);
  response.headers.set("Cache-Control", "no-store");
  return response;
}
