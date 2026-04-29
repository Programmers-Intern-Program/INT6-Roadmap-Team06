export type NavigationItem = {
  href: string;
  label: string;
  activePrefix?: string;
  exact?: boolean;
};

export type ActionLink = {
  label: string;
  href: string;
};

export type ScreenDefinition = {
  eyebrow: string;
  title: string;
  description: string;
  states: string[];
  primaryAction: ActionLink;
  secondaryAction?: ActionLink;
  sections: {
    title: string;
    items: string[];
  }[];
};

export const navigationItems: NavigationItem[] = [
  { href: "/", label: "대시보드", exact: true },
  { href: "/profile", label: "프로필", activePrefix: "/profile" },
  { href: "/github", label: "GitHub", exact: true },
  {
    href: "/github/analysis",
    label: "분석 보정",
    activePrefix: "/github/analysis"
  },
  {
    href: "/diagnoses/demo",
    label: "진단",
    activePrefix: "/diagnoses"
  },
  { href: "/roadmaps/new", label: "로드맵 생성", exact: true },
  {
    href: "/roadmaps/demo",
    label: "로드맵",
    activePrefix: "/roadmaps"
  }
];

export const screens = {
  dashboard: {
    eyebrow: "v1 확장",
    title: "대시보드",
    description:
      "프로필, GitHub 분석, 진단, 로드맵의 최신 상태를 한 화면에서 확인하는 진입점입니다.",
    states: ["최신 결과 조회", "상세 화면 이동", "진도 요약 확인"],
    primaryAction: { label: "프로필 입력", href: "/profile" },
    secondaryAction: { label: "로드맵 보기", href: "/roadmaps/demo" },
    sections: [
      {
        title: "표시 예정 데이터",
        items: [
          "최근 프로필 요약",
          "최종 기술 프로필과 보정 개수",
          "최근 진단 결과와 로드맵 진행률"
        ]
      }
    ]
  },
  profile: {
    eyebrow: "v1 필수",
    title: "프로필 입력 / 수정",
    description:
      "목표 직무, 현재 수준, 기술 스택, 관심 분야와 학습 가능 시간을 저장하는 시작 화면입니다.",
    states: ["입력 전", "저장 중", "저장 완료"],
    primaryAction: { label: "GitHub 연동으로 이동", href: "/github" },
    secondaryAction: { label: "대시보드로 이동", href: "/" },
    sections: [
      {
        title: "입력 예정 항목",
        items: [
          "목표 직무와 현재 수준",
          "사용자 입력 기술 스택",
          "관심 분야, 주당 학습 시간, 목표 날짜"
        ]
      }
    ]
  },
  githubConnection: {
    eyebrow: "v1 필수",
    title: "GitHub 연동 / 저장소 선택",
    description:
      "GitHub 계정을 연결하고 분석 대상 저장소와 핵심 repo를 선택하는 화면입니다.",
    states: ["연결 전", "저장소 불러오는 중", "분석 시작 가능"],
    primaryAction: { label: "분석 결과 확인", href: "/github/analysis" },
    secondaryAction: { label: "프로필로 돌아가기", href: "/profile" },
    sections: [
      {
        title: "선택 예정 항목",
        items: [
          "GitHub OAuth 연결 상태",
          "전체 정적 분석 대상 저장소",
          "LLM 요약 대상 핵심 repo"
        ]
      }
    ]
  },
  githubAnalysis: {
    eyebrow: "v1 필수",
    title: "GitHub 분석 결과 / 사용자 보정",
    description:
      "정적 분석 결과와 근거를 확인하고 사용자 보정값을 저장하는 화면입니다.",
    states: ["분석 완료", "보정 전", "보정 저장 완료"],
    primaryAction: { label: "진단 결과 확인", href: "/diagnoses/demo" },
    secondaryAction: { label: "저장소 선택으로 이동", href: "/github" },
    sections: [
      {
        title: "표시 예정 데이터",
        items: [
          "정적 분석 요약과 repo 요약",
          "기술 태그, 사용 깊이 후보, 근거",
          "사용자 보정과 최종 기술 프로필"
        ]
      }
    ]
  },
  diagnosisDetail: {
    eyebrow: "v1 필수",
    title: "역량 진단 결과",
    description:
      "GitHub 최종 분석이 반영된 부족 기술, 강점, 추천 우선순위를 확인하는 화면입니다.",
    states: ["진단 결과 조회", "부족 기술 확인", "로드맵 생성 가능"],
    primaryAction: { label: "로드맵 생성", href: "/roadmaps/new" },
    secondaryAction: { label: "분석 보정으로 이동", href: "/github/analysis" },
    sections: [
      {
        title: "표시 예정 데이터",
        items: ["역량 요약", "부족 기술 목록", "강점과 추천 우선순위"]
      }
    ]
  },
  roadmapCreate: {
    eyebrow: "v1 필수",
    title: "학습 로드맵 생성",
    description:
      "진단 결과를 기준으로 학습 가능 시간과 목표 날짜를 확인한 뒤 로드맵을 생성하는 화면입니다.",
    states: ["진단 선택", "생성 요청", "생성 완료"],
    primaryAction: { label: "로드맵 보기", href: "/roadmaps/demo" },
    secondaryAction: { label: "진단 결과로 이동", href: "/diagnoses/demo" },
    sections: [
      {
        title: "입력 예정 항목",
        items: ["진단 결과 ID", "주당 학습 시간 override", "목표 날짜 override"]
      }
    ]
  },
  roadmapDetail: {
    eyebrow: "v1 필수",
    title: "학습 로드맵 / 진도 관리",
    description:
      "주차별 학습 계획과 추천 자료를 확인하고 최신 진도 상태를 저장하는 화면입니다.",
    states: ["로드맵 조회", "주차별 상태 확인", "진도 저장"],
    primaryAction: { label: "대시보드로 이동", href: "/" },
    secondaryAction: { label: "로드맵 생성으로 이동", href: "/roadmaps/new" },
    sections: [
      {
        title: "표시 예정 데이터",
        items: [
          "주차별 학습 주제와 추천 자료",
          "표시용 weekNumber와 저장용 roadmapWeekId",
          "최신 progressStatus, progressNote, progressUpdatedAt"
        ]
      }
    ]
  }
} satisfies Record<string, ScreenDefinition>;

export function isNavigationItemActive(
  item: NavigationItem,
  pathname: string
) {
  if (item.exact) {
    return pathname === item.href;
  }

  if (item.href === "/roadmaps/demo") {
    return pathname.startsWith("/roadmaps/") && pathname !== "/roadmaps/new";
  }

  return pathname.startsWith(item.activePrefix ?? item.href);
}
