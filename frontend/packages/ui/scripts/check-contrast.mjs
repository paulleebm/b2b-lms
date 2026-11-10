// Verifies WCAG 2.2 contrast for every token pair DESIGN.md relies on.
// Reads the values straight from src/styles.css so the check can never drift from the code.
// Usage: node scripts/check-contrast.mjs   (exit 1 on any failure)
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const css = readFileSync(fileURLToPath(new URL("../src/styles.css", import.meta.url)), "utf8");
const root = css.slice(css.indexOf(":root"), css.indexOf("}", css.indexOf(":root")));
const tokens = Object.fromEntries(
  [...root.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-f]{6})/gi)].map(([, name, hex]) => [name, hex.toLowerCase()]),
);

const TEXT = 4.5; // WCAG 1.4.3 body text
const UI = 3; // WCAG 1.4.11 component boundaries, focus indicators, large text

/** [foreground, background, minimum, why] */
const pairs = [
  ["ink", "paper", TEXT, "body text on app ground"],
  ["ink", "surface", TEXT, "body text on panels"],
  ["ink-2", "surface", TEXT, "secondary text"],
  ["ink-2", "surface-sunken", TEXT, "table header text"],
  ["muted", "surface", TEXT, "descriptions, placeholders"],
  ["muted", "paper", TEXT, "descriptions on ground"],
  ["muted", "surface-sunken", TEXT, "meta text in sunken areas"],
  ["field-border", "surface", UI, "input boundary"],
  ["field-border", "paper", UI, "input boundary on ground"],
  ["shell-fg", "shell", TEXT, "sidebar menu"],
  ["shell-muted", "shell", TEXT, "sidebar group label"],
  ["shell-fg", "shell-active", TEXT, "current menu item"],
  ["shell-ring", "shell", UI, "focus ring inside the sidebar"],
  ["primary-fg", "primary", TEXT, "primary button"],
  ["primary-fg", "primary-hover", TEXT, "primary button hover"],
  ["accent", "surface", UI, "focus ring, progress fill"],
  ["accent", "paper", UI, "focus ring on ground"],
  ["accent", "surface", TEXT, "links"],
  ["ink", "selection", TEXT, "selected text"],
  ["shell", "shell-fg", UI, "brand mark icon on the sidebar"],
  ["success", "success-soft", TEXT, "success notice, completed badge, completion mark"],
  ["warning", "warning-soft", TEXT, "due-soon badge"],
  ["danger", "danger-soft", TEXT, "overdue badge, error notice"],
  ["danger", "surface", TEXT, "field error text"],
  ["primary-fg", "danger", TEXT, "danger button"],
  ["info", "info-soft", TEXT, "info notice"],
  ["accent", "accent-soft", TEXT, "selected row text"],
];

const channel = (c) => {
  const v = c / 255;
  return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
};
const luminance = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => channel(parseInt(hex.slice(i, i + 2), 16)));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

let failed = 0;
for (const [fg, bg, min, why] of pairs) {
  if (!tokens[fg] || !tokens[bg]) {
    console.error(`MISSING  --${fg} or --${bg} (${why})`);
    failed++;
    continue;
  }
  const r = ratio(tokens[fg], tokens[bg]);
  const ok = r >= min;
  if (!ok) failed++;
  console.log(`${ok ? "ok  " : "FAIL"}  ${r.toFixed(2).padStart(5)} ≥ ${min}  --${fg} on --${bg}  (${why})`);
}

console.log(`\n${pairs.length - failed}/${pairs.length} pairs pass WCAG 2.2 AA`);
process.exit(failed ? 1 : 0);
