import "server-only";

import { randomBytes, randomUUID } from "node:crypto";
import { cookies } from "next/headers";
import { cache } from "react";
import * as oidc from "openid-client";
import { z } from "zod";

import { getOidcConfig } from "./oidc";
import { redis } from "./redis";

/**
 * `__Host-` prefix: the browser only accepts the cookie if it is Secure, Path=/ and has no
 * Domain, so a sibling subdomain cannot overwrite it. Browsers treat http://localhost as a
 * secure context, so the same attributes work in dev.
 */
export const SESSION_COOKIE = "__Host-session";

export const sessionCookieOptions = {
  httpOnly: true,
  secure: true,
  sameSite: "lax",
  path: "/",
} as const;

/** Refresh when the access token expires within this window. */
const REFRESH_SKEW_MS = 30_000;
/** Used when the token response carries no refresh_expires_in (Keycloak-specific field). */
const FALLBACK_SESSION_TTL_S = 30 * 60;
const LOCK_TTL_S = 10;
const LOCK_WAIT_MS = 2_000;
const LOCK_POLL_MS = 100;

/** 32 random bytes, base64url → 43 chars. Anything else is rejected before touching Redis. */
const SESSION_ID_RE = /^[A-Za-z0-9_-]{43}$/;

const sessionSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string().optional(),
  idToken: z.string().optional(),
  /** Access token expiry, epoch ms. */
  expiresAt: z.number(),
  sub: z.string(),
});

export type Session = z.infer<typeof sessionSchema>;

const sessionKey = (id: string) => `session:${id}`;
const lockKey = (id: string) => `refresh-lock:${id}`;

export function newSessionId(): string {
  return randomBytes(32).toString("base64url");
}

export function isValidSessionId(id: string | undefined): id is string {
  return typeof id === "string" && SESSION_ID_RE.test(id);
}

type TokenResponse = oidc.TokenEndpointResponse & oidc.TokenEndpointResponseHelpers;

/** Build a session value from a token response, keeping previous values the IdP did not resend. */
export function sessionFromTokens(
  tokens: TokenResponse,
  previous?: Session,
): { session: Session; ttlSeconds: number } {
  const sub = tokens.claims()?.sub ?? previous?.sub;
  if (!sub) throw new Error("Token response has no subject");

  const expiresIn = tokens.expiresIn() ?? 60;
  const refreshToken = tokens.refresh_token ?? previous?.refreshToken;
  const refreshExpiresIn =
    typeof tokens.refresh_expires_in === "number" && tokens.refresh_expires_in > 0
      ? tokens.refresh_expires_in
      : undefined;

  const ttlSeconds = refreshToken
    ? (refreshExpiresIn ?? FALLBACK_SESSION_TTL_S)
    : Math.max(expiresIn, 1);

  return {
    session: {
      accessToken: tokens.access_token,
      refreshToken,
      idToken: tokens.id_token ?? previous?.idToken,
      expiresAt: Date.now() + expiresIn * 1000,
      sub,
    },
    ttlSeconds,
  };
}

export async function saveSession(id: string, session: Session, ttlSeconds: number) {
  await redis().set(sessionKey(id), JSON.stringify(session), "EX", ttlSeconds);
}

export async function loadSession(id: string): Promise<Session | null> {
  const raw = await redis().get(sessionKey(id));
  if (!raw) return null;
  const parsed = sessionSchema.safeParse(JSON.parse(raw));
  return parsed.success ? parsed.data : null;
}

export async function deleteSession(id: string) {
  await redis().del(sessionKey(id), lockKey(id));
}

/** Session id from the request cookie (syntactically valid ids only). */
export async function getSessionId(): Promise<string | null> {
  const value = (await cookies()).get(SESSION_COOKIE)?.value;
  return isValidSessionId(value) ? value : null;
}

/**
 * Current session for server components, server actions and route handlers.
 * Transparently refreshes the access token when it is about to expire.
 * Memoised per request with React `cache()` so parallel components share one lookup.
 */
export const getSession = cache(async (): Promise<Session | null> => {
  const id = await getSessionId();
  if (!id) return null;

  const session = await loadSession(id);
  if (!session) return null;
  if (!isExpiring(session)) return session;

  return refreshSession(id);
});

function isExpiring(session: Session): boolean {
  return session.expiresAt - Date.now() <= REFRESH_SKEW_MS;
}

/**
 * Refresh under a per-session Redis lock. Keycloak rotates refresh tokens and treats reuse
 * as theft, so two concurrent refreshes of the same session would log the user out.
 * Only the lock holder calls Keycloak; everyone else waits and re-reads the session.
 */
async function refreshSession(id: string): Promise<Session | null> {
  const lockToken = randomUUID();
  const acquired = await redis().set(lockKey(id), lockToken, "EX", LOCK_TTL_S, "NX");

  if (acquired !== "OK") {
    return waitForRefresh(id);
  }

  try {
    // Another request may have refreshed between our read and taking the lock.
    const current = await loadSession(id);
    if (!current) return null;
    if (!isExpiring(current)) return current;

    if (!current.refreshToken) {
      await deleteSession(id);
      return null;
    }

    const config = await getOidcConfig();
    const tokens = await oidc.refreshTokenGrant(config, current.refreshToken);
    const { session, ttlSeconds } = sessionFromTokens(tokens, current);
    await saveSession(id, session, ttlSeconds);
    return session;
  } catch (error) {
    if (isOAuthRejection(error)) {
      // Refresh token expired, revoked or reuse-detected: the session is dead.
      await deleteSession(id);
      return null;
    }
    // Network / IdP outage: keep the session so a transient failure doesn't log users out.
    throw error;
  } finally {
    await releaseLock(id, lockToken);
  }
}

async function waitForRefresh(id: string): Promise<Session | null> {
  const deadline = Date.now() + LOCK_WAIT_MS;
  while (Date.now() < deadline) {
    await sleep(LOCK_POLL_MS);
    const session = await loadSession(id);
    if (!session) return null;
    if (!isExpiring(session)) return session;
  }
  // Holder is slow; use the old token only if it has not actually expired yet.
  const session = await loadSession(id);
  return session && session.expiresAt > Date.now() ? session : null;
}

// Delete the lock only if we still own it (it may have expired and been re-acquired).
const RELEASE_LOCK_LUA = `
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("del", KEYS[1])
end
return 0`;

async function releaseLock(id: string, token: string) {
  try {
    await redis().eval(RELEASE_LOCK_LUA, 1, lockKey(id), token);
  } catch (error) {
    console.error("[session] failed to release refresh lock", error);
  }
}

function isOAuthRejection(error: unknown): boolean {
  return (
    error instanceof oidc.ResponseBodyError ||
    error instanceof oidc.WWWAuthenticateChallengeError
  );
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
