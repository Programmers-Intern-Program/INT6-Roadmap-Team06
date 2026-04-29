"use client";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { githubLoginUrl } from "@/lib/api";

function LoginInner() {
  const params = useSearchParams();
  const error = params.get("error");

  return (
    <>
      <h1>로그인</h1>
      {error && (
        <p className="error">
          OAuth 실패: <code>{error}</code>
        </p>
      )}
      <p>
        <a href={githubLoginUrl()}>
          <button className="btn-primary">GitHub으로 다시 시도</button>
        </a>
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
