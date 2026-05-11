"use client";

import { useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { connectGithub } from "@/features/github-connection/api";
import { StatePanel } from "@/components/state-panel";
import { isUnauthorizedError, getLoginPath } from "@/lib/auth";

function CallbackInner() {
  const router = useRouter();
  const params = useSearchParams();
  const called = useRef(false);

  useEffect(() => {
    if (called.current) return;
    called.current = true;

    const error = params.get("error");
    const code = params.get("code");

    if (error || !code) {
      router.replace(`/github?error=${encodeURIComponent(error ?? "no_code")}`);
      return;
    }

    connectGithub(code)
      .then(() => {
        router.replace("/github");
      })
      .catch((error) => {
        if (isUnauthorizedError(error)) {
          router.replace(getLoginPath("/github"));
          return;
        }
        router.replace("/github?error=connection_failed");
      });
  }, [params, router]);

  return <StatePanel message="GitHub 연결 중입니다... 저장소 수가 많으면 1~3분 소요될 수 있습니다." />;
}

export default function GithubCallbackPage() {
  return (
    <Suspense fallback={<StatePanel message="GitHub 연결 중입니다... 저장소 수가 많으면 1~3분 소요될 수 있습니다." />}>
      <CallbackInner />
    </Suspense>
  );
}
