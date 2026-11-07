import { NextResponse, type NextRequest } from "next/server";

import { getEnv } from "@/server/env";
import { getSession } from "@/server/session";
import { upstreamHeaders } from "@/server/upstream";

/**
 * BFF pass-through for client components: browser → (session cookie) → here → (Bearer token) → API.
 * The access token never reaches the browser. The API still authorizes every call itself;
 * this route only adds the token.
 *
 * CSRF: the session cookie is SameSite=Lax, and every state-changing request must also carry
 * our own Origin. A form or fetch from another site cannot pass both.
 */
const SEGMENT = /^[A-Za-z0-9_-]+$/;
const REQUEST_HEADERS = ["content-type", "if-match", "accept", "idempotency-key"];
// set-cookie: CloudFront signed cookies for HLS (AWS), set by the API's lesson launch response.
const RESPONSE_HEADERS = ["content-type", "etag", "idempotent-replayed", "set-cookie"];

function problem(status: number, code: string, detail: string) {
  return NextResponse.json(
    { type: `https://lms.example/problems/${code.toLowerCase().replaceAll("_", "-")}`, status, code, detail },
    { status, headers: { "Content-Type": "application/problem+json" } },
  );
}

async function forward(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params;
  if (path.length === 0 || !path.every((segment) => SEGMENT.test(segment))) {
    return problem(404, "HTTP_404", "없는 경로입니다.");
  }
  if (request.method !== "GET" && request.headers.get("origin") !== getEnv().APP_URL) {
    return problem(403, "CSRF_REJECTED", "다른 사이트에서 보낸 요청은 처리하지 않습니다.");
  }

  const session = await getSession();
  if (!session) return problem(401, "UNAUTHENTICATED", "로그인이 필요합니다.");

  const headers = upstreamHeaders({ Authorization: `Bearer ${session.accessToken}` });
  for (const name of REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const hasBody = !["GET", "HEAD", "DELETE"].includes(request.method);
  const upstream = await fetch(`${getEnv().API_URL}/v1/${path.join("/")}${request.nextUrl.search}`, {
    method: request.method,
    headers,
    body: hasBody ? await request.arrayBuffer() : undefined,
    cache: "no-store",
    redirect: "manual",
  });

  const responseHeaders = new Headers({ "Cache-Control": "no-store" });
  for (const name of RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  return new NextResponse(upstream.status === 204 ? null : upstream.body, {
    status: upstream.status,
    headers: responseHeaders,
  });
}

export { forward as DELETE, forward as GET, forward as PATCH, forward as POST, forward as PUT };
