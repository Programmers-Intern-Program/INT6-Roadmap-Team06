"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { ApiError } from "@/lib/api";
import {
  getCurrentUser,
  logoutSession,
  refreshSession,
  type CurrentUser
} from "@/features/auth/api";

export default function MePage() {
  const router = useRouter();
  const [me, setMe] = useState<CurrentUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [authRequired, setAuthRequired] = useState(false);
  const [loading, setLoading] = useState(true);
  const didInitialLoad = useRef(false);

  const load = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setLoading(true);
    }
    setError(null);
    setAuthRequired(false);
    try {
      setMe(await getCurrentUser());
    } catch (caught) {
      setMe(null);
      if (caught instanceof ApiError && caught.status === 401) {
        setAuthRequired(true);
      } else if (caught instanceof ApiError) {
        setError(`HTTP ${caught.status}`);
      } else {
        setError("계정 정보를 불러오지 못했습니다.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (didInitialLoad.current) {
      return;
    }

    didInitialLoad.current = true;
    void load(false);
  }, [load]);

  const refresh = async () => {
    try {
      await refreshSession();
      await load();
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 401) {
        setError(null);
        setAuthRequired(true);
      } else if (caught instanceof ApiError) {
        setAuthRequired(false);
        setError(`refresh 실패: HTTP ${caught.status}`);
      } else {
        setAuthRequired(false);
        setError("refresh 실패");
      }
    }
  };

  const logout = async () => {
    await logoutSession();
    router.push("/");
  };

  return (
    <div className="screen-shell">
      <div className="screen-hero">
        <p className="eyebrow">계정 관리</p>
        <div className="screen-heading">
          <h1>내 계정</h1>
          <p>사용자 정보 및 계정 설정을 확인합니다.</p>
        </div>
      </div>

      {loading && (
        <div className="panel">
          <p>불러오는 중…</p>
        </div>
      )}

      {!loading && authRequired && (
        <AuthRequiredPanel redirectPath="/me" standalone={false} />
      )}

      {error && (
        <div className="panel" style={{ borderLeft: "4px solid #dc2626" }}>
          <p style={{ color: "#dc2626", marginBottom: "0.5rem" }}>오류</p>
          <p>{error}</p>
        </div>
      )}

      {me && (
        <div className="panel dashboard-summary-card">
          <h2>사용자 정보</h2>
          <dl className="dashboard-metric-list">
            <div>
              <dt>사용자 ID</dt>
              <dd>{me.userId}</dd>
            </div>
            <div>
              <dt>이메일</dt>
              <dd>{me.email}</dd>
            </div>
            <div>
              <dt>인증 제공자</dt>
              <dd>{me.authProvider}</dd>
            </div>
          </dl>
        </div>
      )}

      <div className="action-row" aria-label="계정 관리">
        <button className="action-link" onClick={() => void load()}>
          정보 새로고침
        </button>
        <button className="action-link" onClick={refresh}>
          토큰 갱신
        </button>
        <button className="action-link" onClick={logout}>
          로그아웃
        </button>
      </div>

      <p style={{ fontSize: "0.875rem", color: "#6b7280", marginTop: "2rem", paddingTop: "1rem", borderTop: "1px solid #e5e7eb" }}>
        accessToken/refreshToken 쿠키는 HttpOnly 라 JS에서 보이지 않는다 — 정상 동작이다.
      </p>
    </div>
  );
}
