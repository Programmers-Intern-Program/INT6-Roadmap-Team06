"use client";

import { useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { connectGithub } from "@/features/github-connection/api";
import { StatePanel } from "@/components/state-panel";

const CONNECTION_ID_KEY = "githubConnectionId";

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
      .then((connection) => {
        localStorage.setItem(CONNECTION_ID_KEY, connection.githubConnectionId);
        router.replace("/github");
      })
      .catch(() => {
        router.replace("/github?error=connection_failed");
      });
  }, [params, router]);

  return <StatePanel message="GitHub 연결 중입니다..." />;
}

export default function GithubCallbackPage() {
  return (
    <Suspense fallback={<StatePanel message="GitHub 연결 중입니다..." />}>
      <CallbackInner />
    </Suspense>
  );
}
