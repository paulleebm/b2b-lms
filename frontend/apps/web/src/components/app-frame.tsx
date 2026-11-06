"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { House } from "lucide-react";
import { AppShell, type NavSection } from "@lms/ui";

/**
 * Client wrapper around the design-system AppShell. Server components cannot pass icon
 * components (functions) across the client boundary, so navigation is built here.
 * Only routes that exist are listed; role-specific menus (DESIGN.md §4 Navigation) arrive
 * with their pages from Day 2.
 */
const NAV: NavSection[] = [{ items: [{ href: "/app", label: "홈", icon: House }] }];

export function AppFrame({
  workspace,
  account,
  children,
}: {
  workspace: React.ReactNode;
  account: React.ReactNode;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const sections = NAV.map((s) => ({ ...s, items: s.items.map((i) => ({ ...i, current: i.href === pathname })) }));
  return (
    <AppShell workspace={workspace} account={account} sections={sections} linkAs={Link}>
      {children}
    </AppShell>
  );
}
