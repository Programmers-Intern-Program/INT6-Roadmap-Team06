"use client";

import { useEffect, useState } from "react";

import { getMyProfile } from "@/features/profile/api";
import {
  currentLevelLabels,
  currentLevelOptions,
  proficiencyLevelLabels
} from "@/features/profile/labels";
import type { ProfileDetail, ProfileSkill } from "@/features/profile/types";
import { ApiError } from "@/lib/api";

type ProfileState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { profile: ProfileDetail | null; status: "ready" };

export function ProfileView() {
  const [state, setState] = useState<ProfileState>({ status: "loading" });

  useEffect(() => {
    let ignore = false;

    async function loadProfile() {
      setState({ status: "loading" });

      try {
        const profile = await getMyProfile();

        if (!ignore) {
          setState({ profile, status: "ready" });
        }
      } catch (error) {
        if (ignore) {
          return;
        }

        if (error instanceof ApiError && error.status === 404) {
          setState({ profile: null, status: "ready" });
          return;
        }

        setState({
          message: getErrorMessage(error),
          status: "error"
        });
      }
    }

    loadProfile();

    return () => {
      ignore = true;
    };
  }, []);

  if (state.status === "loading") {
    return <ProfileStatePanel message="프로필을 불러오는 중입니다." />;
  }

  if (state.status === "error") {
    return <ProfileStatePanel message={state.message} tone="danger" />;
  }

  return (
    <section className="profile-page" aria-labelledby="profile-title">
      <div className="screen-hero">
        <p className="eyebrow">v1 필수</p>
        <div className="screen-heading">
          <h1 id="profile-title">프로필 입력 / 수정</h1>
          <p>
            목표 직무, 현재 수준, 기술 스택과 학습 가능 시간을 저장하는
            시작 화면입니다.
          </p>
        </div>
      </div>

      <ProfileForm profile={state.profile} />
    </section>
  );
}

function ProfileForm({ profile }: { profile: ProfileDetail | null }) {
  return (
    <form className="profile-form" key={profile?.profileId ?? "new"}>
      <section className="panel profile-form-section">
        <div className="profile-section-heading">
          <h2>기본 정보</h2>
          <p>
            목표 직무는 백엔드 job role code를 입력합니다. 예: BACKEND_ENGINEER
          </p>
        </div>

        <label>
          <span>목표 직무</span>
          <input
            defaultValue={profile?.targetRole ?? "BACKEND_ENGINEER"}
            maxLength={100}
            name="targetRole"
            placeholder="BACKEND_ENGINEER"
            required
          />
        </label>

        <label>
          <span>현재 수준</span>
          <select defaultValue={profile?.currentLevel ?? "JUNIOR"} name="currentLevel">
            {currentLevelOptions.map((level) => (
              <option key={level} value={level}>
                {currentLevelLabels[level]}
              </option>
            ))}
          </select>
        </label>
      </section>

      <section className="panel profile-form-section">
        <div className="profile-section-heading">
          <h2>기술 스택</h2>
          <p>
            한 줄에 하나씩 입력합니다. 숙련도를 함께 쓰려면
            <code>기술명|숙련도</code> 형식을 사용합니다.
          </p>
        </div>

        <label>
          <span>기술 목록</span>
          <textarea
            defaultValue={formatSkillLines(profile?.skills ?? [])}
            name="skills"
            placeholder={`Spring Boot|BASIC\nPostgreSQL|WORKING`}
            required
            rows={6}
          />
        </label>

        <div className="profile-helper-list" aria-label="숙련도 값">
          {Object.entries(proficiencyLevelLabels).map(([value, label]) => (
            <span key={value}>
              {value}: {label}
            </span>
          ))}
        </div>
      </section>

      <section className="panel profile-form-section">
        <div className="profile-section-heading">
          <h2>학습 조건</h2>
          <p>관심 분야와 주당 학습 가능 시간을 입력합니다.</p>
        </div>

        <label>
          <span>관심 분야</span>
          <textarea
            defaultValue={(profile?.interestAreas ?? []).join("\n")}
            maxLength={600}
            name="interestAreas"
            placeholder={`백엔드\n성능 최적화`}
            rows={4}
          />
        </label>

        <div className="profile-inline-fields">
          <label>
            <span>주당 학습 시간</span>
            <input
              defaultValue={profile?.weeklyStudyHours ?? ""}
              max={40}
              min={1}
              name="weeklyStudyHours"
              placeholder="10"
              type="number"
            />
          </label>

          <label>
            <span>목표 날짜</span>
            <input
              defaultValue={profile?.targetDate ?? ""}
              name="targetDate"
              type="date"
            />
          </label>
        </div>
      </section>

      <p className="profile-form-note">
        프로필 저장은 다음 단계에서 연결됩니다.
      </p>
    </form>
  );
}

function ProfileStatePanel({
  message,
  tone = "neutral"
}: {
  message: string;
  tone?: "danger" | "neutral";
}) {
  return (
    <section className="panel profile-state-panel" data-tone={tone}>
      <p>{message}</p>
    </section>
  );
}

function getErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return "프로필을 불러오지 못했습니다.";
}

function formatSkillLines(skills: ProfileSkill[]) {
  return skills
    .map((skill) =>
      skill.proficiencyLevel
        ? `${skill.skillName}|${skill.proficiencyLevel}`
        : skill.skillName
    )
    .join("\n");
}
