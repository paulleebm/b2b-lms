import "server-only";

import { z } from "zod";

import type { ProblemDetails } from "./api-types";
import { getEnv } from "./env";
import { getSession } from "./session";
import { upstreamHeaders } from "./upstream";

/** Non-2xx response from the Spring API, parsed from RFC 9457 problem+json when available. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetails;

  constructor(status: number, problem: ProblemDetails) {
    super(problem.detail ?? problem.title ?? `API request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }

  get code(): string | undefined {
    return this.problem.code;
  }

  get traceId(): string | undefined {
    return this.problem.traceId;
  }
}

/** No (valid) BFF session: the caller should send the user through /auth/login. */
export class UnauthenticatedError extends Error {
  constructor() {
    super("No active session");
    this.name = "UnauthenticatedError";
  }
}

const problemSchema = z
  .object({
    type: z.string().optional(),
    title: z.string().optional(),
    status: z.number().int().optional(),
    detail: z.string().optional(),
    instance: z.string().optional(),
    code: z.string().optional(),
    traceId: z.string().optional(),
  })
  .loose();

/**
 * Call the Spring API as the current user. Server-only: the access token never leaves
 * the Next.js server. `path` must be an absolute API path such as `/v1/me`.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  return (await apiFetchWithEtag<T>(path, init)).data;
}

/** Same as {@link apiFetch}, plus the response's ETag (course versions for If-Match). */
export async function apiFetchWithEtag<T>(
  path: string,
  init: RequestInit = {},
): Promise<{ data: T; etag: string | null }> {
  if (!path.startsWith("/") || path.startsWith("//")) {
    throw new Error(`apiFetch expects an absolute API path, got "${path}"`);
  }

  const session = await getSession();
  if (!session) throw new UnauthenticatedError();

  const headers = upstreamHeaders(init.headers);
  headers.set("Authorization", `Bearer ${session.accessToken}`);
  if (!headers.has("Accept")) headers.set("Accept", "application/json, application/problem+json");
  if (init.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${getEnv().API_URL}${path}`, {
    ...init,
    headers,
    cache: "no-store",
    redirect: "manual",
  });

  if (!response.ok) {
    throw await toApiError(response);
  }
  const etag = response.headers.get("ETag");
  if (response.status === 204 || response.headers.get("Content-Length") === "0") {
    return { data: undefined as T, etag };
  }
  return { data: (await response.json()) as T, etag };
}

async function toApiError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get("Content-Type") ?? "";
  let problem: ProblemDetails = { status: response.status, title: response.statusText };

  if (contentType.includes("json")) {
    try {
      const parsed = problemSchema.safeParse(await response.json());
      if (parsed.success) problem = { status: response.status, ...parsed.data };
    } catch {
      // Body was not valid JSON; keep the status-only problem.
    }
  }
  return new ApiError(response.status, problem);
}
