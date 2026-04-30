"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";

type Me = { userId: number; email: string; authProvider: string };

export default function MePage() {
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const didInitialLoad = useRef(false);

  const load = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setLoading(true);
    }
    setError(null);
    const res = await api<Me>("/api/v1/auth/me");
    if (res.ok && res.data) {
      setMe(res.data);
    } else if (res.status === 401) {
      setError("인증 만료 또는 미인증. /refresh 시도하거나 다시 로그인하세요.");
    } else {
      setError(`HTTP ${res.status}`);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    if (didInitialLoad.current) {
      return;
    }

    didInitialLoad.current = true;
    void load(false);
  }, [load]);

  const refresh = async () => {
    const res = await api("/api/v1/auth/refresh", { method: "POST" });
    if (res.ok) await load();
    else setError(`refresh 실패: HTTP ${res.status}`);
  };

  const logout = async () => {
    await api("/api/v1/auth/logout", { method: "POST" });
    router.push("/");
  };

  return (
    <>
      <h1>/me</h1>
      {loading && <p>불러오는 중…</p>}
      {error && <p className="error">{error}</p>}
      {me && <pre>{JSON.stringify(me, null, 2)}</pre>}

      <p>
        <button onClick={() => void load()}>다시 조회</button>{" "}
        <button onClick={refresh}>토큰 갱신 (/refresh)</button>{" "}
        <button onClick={logout}>로그아웃</button>
      </p>

      <p className="muted">
        accessToken/refreshToken 쿠키는 HttpOnly 라 JS에서 보이지 않는다 — 정상 동작이다.
      </p>
    </>
  );
}
