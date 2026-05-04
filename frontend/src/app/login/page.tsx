"use client";
import { useSearchParams } from "next/navigation";
import { Suspense, useSyncExternalStore } from "react";
import { githubLoginUrl } from "@/lib/api";

function subscribeToOrigin() {
  return () => {};
}

function getOriginSnapshot() {
  return window.location.origin;
}

function getServerOriginSnapshot() {
  return "";
}

function LoginInner() {
  const params = useSearchParams();
  const error = params.get("error");
  const origin = useSyncExternalStore(
    subscribeToOrigin,
    getOriginSnapshot,
    getServerOriginSnapshot
  );
  const loginUrl = origin ? githubLoginUrl(`${origin}/me`) : null;

  return (
    <>
      <h1>로그인</h1>
      {error && (
        <p className="error">
          OAuth 실패: <code>{error}</code>
        </p>
      )}
      <p>
        {loginUrl ? (
          <a href={loginUrl}>
            <button className="btn-primary">GitHub으로 다시 시도</button>
          </a>
        ) : (
          <button className="btn-primary" disabled type="button">
            GitHub으로 다시 시도
          </button>
        )}
      </p>
    </>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<p>…</p>}>
      <LoginInner />
    </Suspense>
  );
}
