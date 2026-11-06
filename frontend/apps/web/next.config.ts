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
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
