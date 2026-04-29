"use client";
import { useEffect, useState } from "react";
import { API_BASE, githubLoginUrl } from "@/lib/api";

export default function Home() {
  const [loginHref, setLoginHref] = useState<string>("");

  useEffect(() => {
    setLoginHref(githubLoginUrl());
  }, []);

  return (
    <>
      <h1>Coach — auth smoke test</h1>
      <p className="muted">백엔드: {API_BASE}</p>

      <p>
        <a href={loginHref || "#"}>
          <button className="btn-primary" disabled={!loginHref}>
            GitHub으로 로그인
          </button>
        </a>
      </p>

      <p className="muted">
        성공 시 <a href="/me">/me</a> 로 리다이렉트됩니다.
      </p>
    </>
  );
}
