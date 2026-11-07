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
  // Home deployment: the API sits behind Cloudflare Access, which admits only requests that carry
  // this service token (the Vercel BFF). Unset locally and in AWS, where the API has no public address.
  CF_ACCESS_CLIENT_ID: z.string().min(1).optional(),
  CF_ACCESS_CLIENT_SECRET: z.string().min(1).optional(),
}).refine((env) => !env.CF_ACCESS_CLIENT_ID === !env.CF_ACCESS_CLIENT_SECRET, {
  message: "Set both CF_ACCESS_CLIENT_ID and CF_ACCESS_CLIENT_SECRET, or neither",
  path: ["CF_ACCESS_CLIENT_SECRET"],
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
