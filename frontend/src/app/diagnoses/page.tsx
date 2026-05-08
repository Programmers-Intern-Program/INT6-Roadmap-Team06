"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { StatePanel } from "@/components/state-panel";
import { getDashboard } from "@/features/dashboard/api";
import { ApiError, githubLoginUrl } from "@/lib/api";

export default function DiagnosesPage() {
  const router = useRouter();

  useEffect(() => {
    getDashboard()
      .then((dashboard) => {
        if (dashboard.diagnosis?.diagnosisId) {
          router.replace(`/diagnoses/${dashboard.diagnosis.diagnosisId}`);
        }
      })
      .catch((error) => {
        if (error instanceof ApiError && error.status === 401) {
          window.location.href = githubLoginUrl("/diagnoses");
        }
      });
  }, [router]);

  return (
    <StatePanel
      message="최근 진단 결과를 불러오는 중입니다."
    />
  );
}
