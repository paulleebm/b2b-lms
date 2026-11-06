import "server-only";

import Redis from "ioredis";

import { getEnv } from "./env";

// One connection per server process. Stored on globalThis so dev hot reloads
// don't leak connections.
const globalForRedis = globalThis as typeof globalThis & { __lmsRedis?: Redis };

export function redis(): Redis {
  if (!globalForRedis.__lmsRedis) {
    const client = new Redis(getEnv().REDIS_URL, {
      maxRetriesPerRequest: 2,
      enableAutoPipelining: true,
    });
    client.on("error", (error) => {
      console.error("[redis] connection error:", error.message);
    });
    globalForRedis.__lmsRedis = client;
  }
  return globalForRedis.__lmsRedis;
}
