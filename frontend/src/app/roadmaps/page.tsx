"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

import { StatePanel } from "@/components/state-panel";
import { getDashboard } from "@/features/dashboard/api";
import { ApiError, githubLoginUrl } from "@/lib/api";

export default function RoadmapsPage() {
  const router = useRouter();

  useEffect(() => {
    getDashboard()
      .then((dashboard) => {
        if (dashboard.roadmap?.roadmapId) {
          router.replace(`/roadmaps/${dashboard.roadmap.roadmapId}`);
        } else {
          router.replace("/roadmaps/new");
        }
      })
      .catch((error) => {
        if (error instanceof ApiError && error.status === 401) {
          window.location.href = githubLoginUrl("/roadmaps");
        }
      });
  }, [router]);

  return (
    <StatePanel
      message="최근 로드맵을 불러오는 중입니다."
    />
  );
}
