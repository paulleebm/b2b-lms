const DEFAULT_RETURN_TO = "/app";

/**
 * Only allow same-origin relative paths as post-login targets (prevents open redirects).
 * Rejects absolute URLs, protocol-relative `//host`, and backslash tricks like `/\host`.
 */
export function safeReturnTo(value: string | null | undefined): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) {
    return DEFAULT_RETURN_TO;
  }
  // Control characters (incl. CR/LF) have no place in a redirect target.
  if (/[\u0000-\u001f\u007f]/.test(value)) return DEFAULT_RETURN_TO;
  // Never bounce back into the auth endpoints themselves.
  if (value === "/auth" || value.startsWith("/auth/")) return DEFAULT_RETURN_TO;
  return value;
}

/** Build a login URL. Plain `<a href>` only — never `<Link>`, prefetching would start logins. */
export function loginHref(options: { returnTo?: string; org?: string } = {}): string {
  const params = new URLSearchParams();
  if (options.org) params.set("org", options.org);
  if (options.returnTo) params.set("returnTo", options.returnTo);
  const query = params.toString();
  return query ? `/auth/login?${query}` : "/auth/login";
}
