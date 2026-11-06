import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

export default defineConfig([
  ...nextVitals,
  ...nextTs,
  // src/vendor: third-party source copied as-is (see its README); linted upstream, not here.
  globalIgnores([".next/**", "out/**", "build/**", "next-env.d.ts", "src/vendor/**"]),
]);
