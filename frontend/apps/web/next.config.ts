import type { NextConfig } from "next";

const isDev = process.env.NODE_ENV !== "production";

// The logout form POSTs to /auth/logout, which 303-redirects to Keycloak's end-session
// endpoint. Browsers apply `form-action` to redirects after a form submission, so the
// Keycloak origin must be allowed. Headers are computed at build time; set
// KEYCLOAK_ISSUER when building the production image.
const keycloakOrigin = new URL(
  process.env.KEYCLOAK_ISSUER ?? "http://localhost:8080/realms/lms",
).origin;

// Uploads go from the browser straight to S3 with presigned URLs (the bytes never touch our
// servers), so the bucket's origin must be reachable. Locally that is S3Mock.
const storageOrigin = new URL(process.env.STORAGE_ORIGIN ?? "http://localhost:9090").origin;

// Next.js injects inline bootstrap scripts, so script-src needs 'unsafe-inline' unless we
// move to per-request nonces (planned once pages stabilise). Dev additionally needs
// 'unsafe-eval' (React Refresh) and websocket connections (HMR).
const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  `connect-src 'self' ${storageOrigin}${isDev ? " ws: wss:" : ""}`,
  `media-src 'self' blob: ${storageOrigin}`,
  `form-action 'self' ${keycloakOrigin}`,
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "object-src 'none'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: contentSecurityPolicy },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  output: "standalone",
  // The floating dev-tools badge overlaps the bottom-left of pages in screenshots and demos.
  devIndicators: false,
  poweredByHeader: false,
  reactStrictMode: true,
  // @lms/ui ships TypeScript source; let Next compile it.
  transpilePackages: ["@lms/ui", "@lms/api-client"],
  // One instance per process: the OIDC configuration is cached on globalThis, and openid-client
  // checks `instanceof Configuration`. Bundled per route, each route got its own copy of the class
  // and the cached object failed the check in every route but the one that created it.
  serverExternalPackages: ["openid-client"],
  experimental: {
    // The dev MCP endpoint mirrors every log line into .next/dev/logs. On this Windows monorepo
    // that write was seen as a change, so each request triggered a rebuild and a full reload:
    // pages refreshed forever and Playwright navigations were aborted.
    mcpServer: false,
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
