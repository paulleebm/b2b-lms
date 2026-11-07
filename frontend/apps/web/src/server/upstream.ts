import "server-only";

import { getEnv } from "./env";

/**
 * Headers every server-side call to the Spring API carries besides the user's token.
 * At home the API is published through a Cloudflare Tunnel behind Cloudflare Access: requests
 * without the BFF's service token are rejected at Cloudflare's edge and never reach the mini PC.
 */
export function upstreamHeaders(init?: HeadersInit): Headers {
  const headers = new Headers(init);
  const { CF_ACCESS_CLIENT_ID, CF_ACCESS_CLIENT_SECRET } = getEnv();
  if (CF_ACCESS_CLIENT_ID && CF_ACCESS_CLIENT_SECRET) {
    headers.set("CF-Access-Client-Id", CF_ACCESS_CLIENT_ID);
    headers.set("CF-Access-Client-Secret", CF_ACCESS_CLIENT_SECRET);
  }
  return headers;
}
