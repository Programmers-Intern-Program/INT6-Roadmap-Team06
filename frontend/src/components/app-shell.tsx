"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

import { isNavigationItemActive, navigationItems } from "@/config/routes";

type AppShellProps = {
  children: ReactNode;
};

export function AppShell({ children }: AppShellProps) {
  const pathname = usePathname();

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link className="brand" href="/">
          AI Growth Coach
        </Link>
        <nav className="topnav" aria-label="v1 화면 이동">
          {navigationItems.map((item) => {
            const isActive = isNavigationItemActive(item, pathname);

            return (
              <Link
                aria-current={isActive ? "page" : undefined}
                className="nav-link"
                data-active={isActive}
                href={item.href}
                key={item.href}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      </header>
      <main className="page">{children}</main>
    </div>
  );
}
