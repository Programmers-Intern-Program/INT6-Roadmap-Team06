"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import { getDashboard } from "@/features/dashboard/api";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type RoadmapsPageState =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "error"; message: string };

export default function RoadmapsPage() {
  const router = useRouter();
  const [state, setState] = useState<RoadmapsPageState>({
    status: "loading"
  });

  useEffect(() => {
    let ignore = false;

    getDashboard()
      .then((dashboard) => {
        if (ignore) return;
        if (dashboard.roadmap?.roadmapId) {
          router.replace(`/roadmaps/${dashboard.roadmap.roadmapId}`);
        } else {
          router.replace("/roadmaps/new");
        }
      })
      .catch((error) => {
        if (ignore) return;
        if (isUnauthorizedError(error)) {
          setState({ status: "auth-required" });
          return;
        }
        setState({ message: getErrorMessage(error), status: "error" });
      });

    return () => {
      ignore = true;
    };
  }, [router]);

  if (state.status === "auth-required") {
    return (
      <AuthRequiredPanel
        className="roadmap-state-panel"
        redirectPath="/roadmaps"
      />
    );
  }

  if (state.status === "error") {
    return (
      <StatePanel
        className="roadmap-state-panel"
        message={state.message}
        tone="danger"
      />
    );
  }

  return (
    <StatePanel
      message="최근 로드맵을 불러오는 중입니다."
    />
  );
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "최근 로드맵을 불러오지 못했습니다.";
}
