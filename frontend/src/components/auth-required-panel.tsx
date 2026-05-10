"use client";

import Link from "next/link";

import { StatePanel } from "@/components/state-panel";
import { getLoginPath } from "@/lib/auth";

type AuthRequiredPanelProps = {
  className?: string;
  message?: string;
  redirectPath?: string;
  standalone?: boolean;
};

export function AuthRequiredPanel({
  className,
  message = "로그인이 필요합니다. 다시 로그인하면 이 화면으로 돌아옵니다.",
  redirectPath,
  standalone = true
}: AuthRequiredPanelProps) {
  const content = (
    <>
      <StatePanel className={className} message={message} tone="danger" />
      <div className="action-row" aria-label="인증 복구">
        <Link className="action-link primary" href={getLoginPath(redirectPath)}>
          다시 로그인
        </Link>
        <Link className="action-link" href="/login">
          로그인 화면
        </Link>
      </div>
    </>
  );

  return standalone ? (
    <section className="screen-shell">{content}</section>
  ) : (
    content
  );
}
