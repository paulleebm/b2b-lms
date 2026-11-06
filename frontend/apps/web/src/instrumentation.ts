// Runs once when the Next.js server boots: fail fast on missing/invalid configuration
// instead of on the first login attempt.
export async function register() {
  if (process.env.NEXT_RUNTIME !== "nodejs") return;
  if (process.env.NEXT_PHASE === "phase-production-build") return;

  const { loadEnv } = await import("./server/env-schema");
  loadEnv();
}
