import { NextResponse, type NextRequest } from "next/server";

// Next.js 16 renamed middleware.ts → proxy.ts (same role: runs before routing).
// This is only a cheap presence check to bounce anonymous users to login early.
// Real validation (Redis lookup, token refresh) happens server-side in getSession().
const SESSION_COOKIE = "__Host-session";

export function proxy(request: NextRequest) {
  if (request.cookies.has(SESSION_COOKIE)) {
    return NextResponse.next();
  }

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = "/auth/login";
  loginUrl.search = "";
  loginUrl.searchParams.set("returnTo", `${request.nextUrl.pathname}${request.nextUrl.search}`);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/app/:path*", "/onboarding"],
};
