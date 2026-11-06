import type { ProblemDetails } from "@lms/api-client";

/** A non-2xx answer from the API (through the BFF), with its problem+json body. */
export class ClientApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetails;

  constructor(status: number, problem: ProblemDetails) {
    super(problem.detail ?? `요청이 실패했습니다 (${status})`);
    this.name = "ClientApiError";
    this.status = status;
    this.problem = problem;
  }

  get code(): string | undefined {
    return this.problem.code;
  }
}

type Method = "GET" | "POST" | "PATCH" | "PUT" | "DELETE";

/**
 * Calls the API from the browser through the BFF (`/api/v1/...`). The session cookie goes along;
 * the access token stays on the server. Returns the body and the ETag (course versions).
 */
export async function api<T>(
  method: Method,
  path: `/v1/${string}`,
  options: { body?: unknown; ifMatch?: string | null } = {},
): Promise<{ data: T; etag: string | null }> {
  const headers: Record<string, string> = { Accept: "application/json, application/problem+json" };
  if (options.body !== undefined) headers["Content-Type"] = "application/json";
  if (options.ifMatch) headers["If-Match"] = options.ifMatch;

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    credentials: "same-origin",
    cache: "no-store",
  });

  if (!response.ok) {
    let problem: ProblemDetails = { status: response.status };
    try {
      problem = { status: response.status, ...((await response.json()) as ProblemDetails) };
    } catch {
      // not JSON: keep the status only
    }
    throw new ClientApiError(response.status, problem);
  }
  const data = response.status === 204 ? (undefined as T) : ((await response.json()) as T);
  return { data, etag: response.headers.get("ETag") };
}

/** One line for a person: the API's Korean `detail`, or a generic fallback. */
export function describeError(error: unknown): { title: string; detail?: string; traceId?: string } {
  if (error instanceof ClientApiError) {
    return { title: error.problem.detail ?? "요청을 처리하지 못했습니다.", traceId: error.problem.traceId };
  }
  return { title: "서버에 연결하지 못했습니다.", detail: "네트워크를 확인하고 다시 시도하세요." };
}
