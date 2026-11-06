# Third-party agent skills

Installed 2026-11-06 after reading each SKILL.md and every bundled script. Licenses are kept next to each skill.

| Skill folder | Source (commit) | License | What we use it for | Notes |
|---|---|---|---|---|
| `frontend-design/` | [anthropics/skills](https://github.com/anthropics/skills/tree/main/skills/frontend-design) @ `ca45fc496a34` | Apache-2.0 (`LICENSE.txt`) | Visual direction for new surfaces; anti-template checklist | Unmodified |
| `impeccable/` | [pbakaus/impeccable](https://github.com/pbakaus/impeccable) `.claude/skills/impeccable` @ `c3bb418ee38e` | Apache-2.0 (`LICENSE`) | `critique`, `audit`, `polish`, `harden`, `layout`, `typeset` passes | **`scripts/` deliberately not installed**: its launcher downloads and executes a native binary and installs edit hooks. The skill's documented "Launcher unavailable" path reads `PRODUCT.md` and `DESIGN.md` directly, which is how we run it |
| `web-design-guidelines/` | [vercel-labs/agent-skills](https://github.com/vercel-labs/agent-skills/tree/main/skills/web-design-guidelines) @ `1a2c79d4e6c4` | MIT (repo README) | Rule-based UI review against Vercel's Web Interface Guidelines | Fetches the rules at review time from `vercel-labs/web-interface-guidelines` (MIT) |
| `accessibility/`, `best-practices/`, `core-web-vitals/`, `performance/`, `seo/`, `web-quality-audit/` | [addyosmani/web-quality-skills](https://github.com/addyosmani/web-quality-skills) @ `afa8da942115` | MIT (`LICENSE`) | WCAG 2.2, Core Web Vitals, Lighthouse-style audits | `web-quality-audit/scripts/analyze.sh` reviewed: local grep only, no network |
