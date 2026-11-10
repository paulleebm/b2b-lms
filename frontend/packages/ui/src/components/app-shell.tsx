"use client";

import * as React from "react";
import { Menu, X, type LucideIcon } from "lucide-react";

import { cn } from "../lib/utils";
import { BrandMark } from "./brand-mark";
import { Button } from "./button";

/**
 * DESIGN.md §4 Navigation, §5 layout, §8 responsive.
 * The ink-navy shell is the widest brand surface; navigation itself is the standard
 * side rail + top bar the audience already knows (Impeccable Operate: the world lends
 * palette, not navigation).
 *
 * Frameworks pass their own link component (Next.js <Link>) through `linkAs`.
 */

type NavItem = {
  href: string;
  label: string;
  icon: LucideIcon;
  current?: boolean;
  /** Count shown after the label, e.g. overdue people. */
  count?: number;
};

type NavSection = { label?: string; items: NavItem[] };

type LinkLike = React.ElementType<{ href: string; className?: string; children?: React.ReactNode; "aria-current"?: "page" }>;

type AppShellProps = {
  product?: string;
  /** Company currently acted for (switcher lives in the top bar). */
  workspace: React.ReactNode;
  sections: NavSection[];
  /** Right side of the top bar: user menu, logout. */
  account: React.ReactNode;
  linkAs?: LinkLike;
  children: React.ReactNode;
};

function SidebarNav({ sections, linkAs: LinkEl = "a", onNavigate }: { sections: NavSection[]; linkAs?: LinkLike; onNavigate?: () => void }) {
  return (
    <nav aria-label="주 메뉴" className="grid gap-6 px-3">
      {sections.map((section, i) => (
        <div key={section.label ?? i} className="grid gap-0.5">
          {section.label ? (
            <p className="px-2.5 pb-1.5 text-label font-medium text-shell-muted">{section.label}</p>
          ) : null}
          <ul className="grid gap-0.5">
            {section.items.map((item) => {
              const Icon = item.icon;
              return (
                <li key={item.href}>
                  <LinkEl
                    href={item.href}
                    aria-current={item.current ? "page" : undefined}
                    className={cn(
                      "relative flex h-9 items-center gap-2.5 rounded-control px-2.5 text-body-sm font-medium text-shell-fg/85 transition-colors duration-150",
                      "hover:bg-shell-active/70 hover:text-shell-fg focus-visible:outline-2 focus-visible:outline-shell-ring focus-visible:-outline-offset-2",
                      item.current &&
                        "bg-shell-active text-shell-fg before:absolute before:inset-y-2 before:left-0 before:w-0.5 before:bg-shell-fg",
                    )}
                    {...(onNavigate ? { onClick: onNavigate } : {})}
                  >
                    <Icon className="size-4 shrink-0 stroke-[1.75]" aria-hidden="true" />
                    <span className="flex-1 truncate">{item.label}</span>
                    {item.count ? (
                      <span className="text-label text-shell-muted tabular" aria-label={`${item.count}건`}>
                        {item.count}
                      </span>
                    ) : null}
                  </LinkEl>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );
}

function AppShell({ product = "LMS", workspace, sections, account, linkAs, children }: AppShellProps) {
  const [menuOpen, setMenuOpen] = React.useState(false);
  const openerRef = React.useRef<HTMLButtonElement>(null);
  const sheetRef = React.useRef<HTMLElement>(null);
  const wasOpen = React.useRef(false);

  // Modal sheet behaviour (WCAG 2.4.3 focus order): focus moves into the sheet, the page behind
  // is `inert` (not focusable, hidden from assistive tech), Escape closes, focus returns to the opener.
  React.useEffect(() => {
    if (menuOpen) {
      wasOpen.current = true;
      sheetRef.current?.querySelector<HTMLElement>("a, button")?.focus();
      const onKey = (e: KeyboardEvent) => e.key === "Escape" && setMenuOpen(false);
      window.addEventListener("keydown", onKey);
      return () => window.removeEventListener("keydown", onKey);
    }
    if (wasOpen.current) {
      wasOpen.current = false;
      openerRef.current?.focus();
    }
  }, [menuOpen]);

  const brand = (
    <div className="flex h-14 items-center px-5">
      <BrandMark name={product} tone="shell" />
    </div>
  );

  return (
    <div data-slot="app-shell" className="min-h-dvh bg-paper lg:grid lg:grid-cols-[240px_1fr]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-control focus:bg-surface focus:px-3 focus:py-2 focus:shadow-overlay"
      >
        본문으로 건너뛰기
      </a>

      {/* Desktop rail */}
      <aside className="sticky top-0 hidden h-dvh flex-col overflow-y-auto bg-shell pb-6 lg:flex">
        {brand}
        <SidebarNav sections={sections} linkAs={linkAs} />
      </aside>

      {/* Mobile sheet */}
      {menuOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div aria-hidden="true" className="absolute inset-0 bg-ink/40" onClick={() => setMenuOpen(false)} />
          <aside
            ref={sheetRef}
            role="dialog"
            aria-modal="true"
            aria-label="주 메뉴"
            className="absolute inset-y-0 left-0 flex w-72 flex-col overflow-y-auto bg-shell pb-6 shadow-overlay"
          >
            <div className="flex items-center justify-between pr-3">
              {brand}
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="메뉴 닫기"
                className="text-shell-fg hover:bg-shell-active hover:text-shell-fg"
                onClick={() => setMenuOpen(false)}
              >
                <X aria-hidden="true" />
              </Button>
            </div>
            <SidebarNav sections={sections} linkAs={linkAs} onNavigate={() => setMenuOpen(false)} />
          </aside>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-col" inert={menuOpen || undefined}>
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-line bg-paper px-4 md:px-8">
          <Button
            ref={openerRef}
            variant="ghost"
            size="icon-sm"
            className="lg:hidden"
            aria-label="메뉴 열기"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen(true)}
          >
            <Menu aria-hidden="true" />
          </Button>
          <div className="min-w-0 flex-1">{workspace}</div>
          <div className="flex shrink-0 items-center gap-2">{account}</div>
        </header>
        <main id="main" tabIndex={-1} className="mx-auto w-full max-w-[1200px] flex-1 px-4 py-6 outline-none md:px-8 md:py-8">
          {children}
        </main>
      </div>
    </div>
  );
}

/** One per page: the page's h1, what it is for, and its primary action. No eyebrow above. */
function PageHeader({
  title,
  description,
  actions,
  className,
}: {
  title: string;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("mb-6 flex flex-wrap items-end justify-between gap-x-6 gap-y-3", className)}>
      <div className="grid min-w-0 gap-1">
        <h1 className="text-headline font-semibold text-ink">{title}</h1>
        {description ? <p className="max-w-[68ch] text-body-sm text-muted">{description}</p> : null}
      </div>
      {actions ? <div className="flex shrink-0 items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export { AppShell, PageHeader, SidebarNav, type AppShellProps, type NavItem, type NavSection };
