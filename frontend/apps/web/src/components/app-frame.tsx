"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { BookOpen, Building2, ClipboardList, GraduationCap, LayoutDashboard, ScrollText, UsersRound } from "lucide-react";
import { AppShell, type NavSection } from "@lms/ui";

/**
 * Client wrapper around the design-system AppShell. Server components cannot pass icon
 * components (functions) across the client boundary, so navigation is built here.
 * Menus follow the role (DESIGN.md §4 Navigation); only pages that exist are listed.
 * Hiding a menu is convenience, not security: the API checks the role on every call.
 */
function navFor(role: string | null): NavSection[] {
  const home: NavSection = { items: [{ href: "/app", label: "내 교육", icon: GraduationCap }] };
  switch (role) {
    case "ORG_ADMIN":
      return [
        home,
        {
          label: "조직",
          items: [
            { href: "/app/members", label: "멤버", icon: UsersRound },
            { href: "/app/departments", label: "부서", icon: Building2 },
            { href: "/app/audit", label: "감사 로그", icon: ScrollText },
          ],
        },
        {
          label: "교육",
          items: [
            { href: "/app/dashboard", label: "교육 현황", icon: LayoutDashboard },
            { href: "/app/assignments", label: "교육 배정", icon: ClipboardList },
            { href: "/app/studio", label: "코스 스튜디오", icon: BookOpen },
          ],
        },
      ];
    case "INSTRUCTOR":
      return [home, { label: "교육", items: [{ href: "/app/studio", label: "내 코스", icon: BookOpen }] }];
    default:
      return [home];
  }
}

/** "내 교육" stays highlighted inside a course and its lessons. */
function isCurrent(href: string, pathname: string): boolean {
  if (href === "/app") return pathname === "/app" || pathname.startsWith("/app/learn/");
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function AppFrame({
  role,
  workspace,
  account,
  children,
}: {
  role: string | null;
  workspace: React.ReactNode;
  account: React.ReactNode;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const sections = navFor(role).map((s) => ({
    ...s,
    items: s.items.map((i) => ({
      ...i,
      current: isCurrent(i.href, pathname),
    })),
  }));
  return (
    <AppShell workspace={workspace} account={account} sections={sections} linkAs={Link}>
      {children}
    </AppShell>
  );
}
