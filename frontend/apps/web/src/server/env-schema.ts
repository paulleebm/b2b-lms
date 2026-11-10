import { z } from "zod";

// Kept free of `server-only` so instrumentation.ts can import it at boot.
// Application code should import `getEnv` from "./env" instead.

const httpUrl = z
  .url({ protocol: /^https?$/ })
  .transform((value) => value.replace(/\/+$/, ""));

export const envSchema = z.object({
  APP_URL: httpUrl,
  KEYCLOAK_ISSUER: httpUrl,
  KEYCLOAK_CLIENT_ID: z.string().min(1),
  KEYCLOAK_CLIENT_SECRET: z.string().min(1),
  REDIS_URL: z.url({ protocol: /^rediss?$/ }),
  API_URL: httpUrl,
});

export type Env = z.infer<typeof envSchema>;

let cached: Env | undefined;

/** Parse and cache process.env. Throws a readable error listing every invalid variable. */
export function loadEnv(): Env {
  if (cached) return cached;
  const result = envSchema.safeParse(process.env);
  if (!result.success) {
    throw new Error(`Invalid environment variables:\n${z.prettifyError(result.error)}`);
  }
  cached = result.data;
  return cached;
}
