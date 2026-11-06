import "server-only";

import * as oidc from "openid-client";

import { getEnv } from "./env";

/** Base scopes. `organization` asks Keycloak to add the organization claim to tokens. */
export const BASE_SCOPES = ["openid", "profile", "email"] as const;

/** Keycloak organization alias == our tenant slug. */
export const ORG_ALIAS_RE = /^[a-z0-9-]{3,30}$/;

const globalForOidc = globalThis as typeof globalThis & {
  __lmsOidcConfig?: Promise<oidc.Configuration>;
};

/**
 * OIDC discovery against Keycloak, cached per process. A failed discovery is not cached,
 * so the next request retries (e.g. Keycloak was still starting).
 */
export function getOidcConfig(): Promise<oidc.Configuration> {
  if (!globalForOidc.__lmsOidcConfig) {
    const env = getEnv();
    const issuer = new URL(env.KEYCLOAK_ISSUER);
    const insecure = issuer.protocol === "http:";
    if (insecure && process.env.NODE_ENV === "production" && !isLocalHost(issuer.hostname)) {
      throw new Error("KEYCLOAK_ISSUER must use https in production");
    }

    globalForOidc.__lmsOidcConfig = oidc
      .discovery(
        issuer,
        env.KEYCLOAK_CLIENT_ID,
        undefined,
        oidc.ClientSecretBasic(env.KEYCLOAK_CLIENT_SECRET),
        insecure ? { execute: [oidc.allowInsecureRequests] } : undefined,
      )
      .catch((error: unknown) => {
        globalForOidc.__lmsOidcConfig = undefined;
        throw error;
      });
  }
  return globalForOidc.__lmsOidcConfig;
}

export function redirectUri(): string {
  return `${getEnv().APP_URL}/auth/callback`;
}

export function postLogoutRedirectUri(): string {
  return `${getEnv().APP_URL}/`;
}

/** `organization` lets the user pick (or auto-selects a single org); `organization:<alias>` forces one. */
export function scopeFor(orgAlias?: string): string {
  return [...BASE_SCOPES, orgAlias ? `organization:${orgAlias}` : "organization"].join(" ");
}

function isLocalHost(hostname: string): boolean {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "[::1]";
}
