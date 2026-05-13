"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import type { ReactNode } from "react";

import { isNavigationItemActive, navigationItems } from "@/config/routes";
import { getCurrentUser } from "@/features/auth/api";
import { CoachWidget } from "@/features/coach/coach-widget";

type AppShellProps = {
  children: ReactNode;
};

export function AppShell({ children }: AppShellProps) {
  const pathname = usePathname();
  const isLoginPage = pathname === "/login";
  const [authState, setAuthState] = useState<"checking" | "authenticated" | "anonymous">(
    "checking"
  );

  useEffect(() => {
    let ignore = false;

    getCurrentUser()
      .then(() => {
        if (!ignore) {
          setAuthState("authenticated");
        }
      })
      .catch(() => {
        if (!ignore) {
          setAuthState("anonymous");
        }
      });

    return () => {
      ignore = true;
    };
  }, [pathname]);

  if (isLoginPage) {
    return <>{children}</>;
  }

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
        <div className="topbar-actions">
          <AuthStatusLink authState={authState} pathname={pathname} />
        </div>
      </header>
      <main className="page">{children}</main>
      <CoachWidget />
    </div>
  );
}

type AuthStatusLinkProps = {
  authState: "checking" | "authenticated" | "anonymous";
  pathname: string;
};

function AuthStatusLink({ authState, pathname }: AuthStatusLinkProps) {
  const item =
    authState === "anonymous"
      ? { exact: true, href: "/login", label: "로그인" }
      : { exact: true, href: "/me", label: authState === "checking" ? "계정" : "내 계정" };
  const isActive = isNavigationItemActive(item, pathname);

  return (
    <Link
      aria-current={isActive ? "page" : undefined}
      className="nav-link"
      data-active={isActive}
      href={item.href}
    >
      {item.label}
    </Link>
  );
}
