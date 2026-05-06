"use client";

import { useEffect, useState, type FormEvent } from "react";

import { StatePanel } from "@/components/state-panel";
import { getMyProfile, saveProfile } from "@/features/profile/api";
import {
  currentLevelLabels,
  currentLevelOptions,
  proficiencyLevelOptions,
  proficiencyLevelLabels
} from "@/features/profile/labels";
import type {
  CurrentLevel,
  ProfileDetail,
  ProfileSaveRequest,
  ProfileSaveResponse,
  ProfileSkill,
  ProficiencyLevel
} from "@/features/profile/types";
import { ApiError } from "@/lib/api";

type ProfileState =
  | { status: "loading" }
  | { status: "error"; message: string }
  | { profile: ProfileDetail | null; status: "ready" };

export function ProfileView() {
  const [state, setState] = useState<ProfileState>({ status: "loading" });
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saveResult, setSaveResult] = useState<ProfileSaveResponse | null>(null);
  const [saving, setSaving] = useState(false);

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
    return (
      <StatePanel
        className="profile-state-panel"
        message="프로필을 불러오는 중입니다."
      />
    );
  }

  if (state.status === "error") {
    return (
      <StatePanel
        className="profile-state-panel"
        message={state.message}
        tone="danger"
      />
    );
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setSaveError(null);
    setSaveResult(null);

    let payload: ProfileSaveRequest;

    try {
      payload = toProfileSaveRequest(new FormData(event.currentTarget));
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "입력 값을 확인해 주세요.");
      return;
    }

    setSaving(true);

    try {
      const result = await saveProfile(payload);
      setSaveResult(result);

      const profile = await getMyProfile();
      setState({ profile, status: "ready" });
    } catch (error) {
      setSaveError(getErrorMessage(error));
    } finally {
      setSaving(false);
    }
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

      <ProfileForm
        onSubmit={handleSubmit}
        profile={state.profile}
        saveError={saveError}
        saveResult={saveResult}
        saving={saving}
      />
    </section>
  );
}

function ProfileForm({
  onSubmit,
  profile,
  saveError,
  saveResult,
  saving
}: {
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  profile: ProfileDetail | null;
  saveError: string | null;
  saveResult: ProfileSaveResponse | null;
  saving: boolean;
}) {
  return (
    <form
      className="profile-form"
      key={profile?.profileId ?? "new"}
      onSubmit={onSubmit}
    >
      <section className="panel profile-form-section">
        <div className="profile-section-heading">
          <h2>기본 정보</h2>
          <p>
            목표 직무는 백엔드 job role code를 입력합니다. 예: BACKEND_DEVELOPER
          </p>
        </div>

        <label>
          <span>목표 직무</span>
          <input
            defaultValue={profile?.targetRole ?? "BACKEND_DEVELOPER"}
            maxLength={100}
            name="targetRole"
            placeholder="BACKEND_DEVELOPER"
            required
          />
        </label>

        <label>
          <span>현재 수준</span>
          <select
            defaultValue={profile?.currentLevel ?? "JUNIOR"}
            name="currentLevel"
          >
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

      <div className="profile-form-actions">
        <div>
          {saveError ? <p className="profile-form-error">{saveError}</p> : null}
          {saveResult ? (
            <p className="profile-form-success">
              {saveResult.message} · {formatDateTime(saveResult.savedAt)}
            </p>
          ) : null}
        </div>
        <button disabled={saving} type="submit">
          {saving ? "저장 중" : "프로필 저장"}
        </button>
      </div>
    </form>
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

function toProfileSaveRequest(formData: FormData): ProfileSaveRequest {
  const targetRole = getTextValue(formData, "targetRole").trim();
  const currentLevel = getTextValue(formData, "currentLevel");
  const skills = parseSkills(getTextValue(formData, "skills"));
  const interestAreas = parseInterestAreas(getTextValue(formData, "interestAreas"));
  const weeklyStudyHours = parseWeeklyStudyHours(
    getTextValue(formData, "weeklyStudyHours")
  );
  const targetDate = parseTargetDate(getTextValue(formData, "targetDate"));

  if (!targetRole) {
    throw new Error("목표 직무를 입력해 주세요.");
  }

  if (!isCurrentLevel(currentLevel)) {
    throw new Error("현재 수준을 선택해 주세요.");
  }

  return {
    currentLevel,
    interestAreas,
    skills,
    targetDate,
    targetRole,
    weeklyStudyHours
  };
}

function parseSkills(value: string): ProfileSaveRequest["skills"] {
  const lines = splitLines(value);

  if (lines.length === 0) {
    throw new Error("기술 스택을 1개 이상 입력해 주세요.");
  }

  if (lines.length > 20) {
    throw new Error("기술 스택은 최대 20개까지 입력할 수 있습니다.");
  }

  const names = new Set<string>();

  return lines.map((line) => {
    const [rawName, rawLevel] = line.split("|").map((part) => part.trim());
    const skillName = rawName ?? "";
    const proficiencyLevel = rawLevel ? rawLevel.toUpperCase() : "";

    if (!skillName) {
      throw new Error("기술명을 입력해 주세요.");
    }

    if (skillName.length > 100) {
      throw new Error("기술명은 100자 이하로 입력해 주세요.");
    }

    const normalizedName = skillName.toLowerCase();

    if (names.has(normalizedName)) {
      throw new Error(`중복된 기술이 있습니다: ${skillName}`);
    }

    names.add(normalizedName);

    if (!proficiencyLevel) {
      return { proficiencyLevel: null, skillName };
    }

    if (!isProficiencyLevel(proficiencyLevel)) {
      throw new Error(`지원하지 않는 숙련도입니다: ${proficiencyLevel}`);
    }

    return { proficiencyLevel, skillName };
  });
}

function parseInterestAreas(value: string) {
  const lines = splitLines(value);

  if (lines.length > 10) {
    throw new Error("관심 분야는 최대 10개까지 입력할 수 있습니다.");
  }

  const tooLong = lines.find((line) => line.length > 50);

  if (tooLong) {
    throw new Error(`관심 분야는 50자 이하로 입력해 주세요: ${tooLong}`);
  }

  return lines;
}

function parseWeeklyStudyHours(value: string) {
  if (!value.trim()) {
    return null;
  }

  const parsed = Number(value);

  if (!Number.isInteger(parsed) || parsed < 1 || parsed > 40) {
    throw new Error("주당 학습 시간은 1~40 사이의 정수로 입력해 주세요.");
  }

  return parsed;
}

function parseTargetDate(value: string) {
  const targetDate = value.trim();

  if (!targetDate) {
    return null;
  }

  const today = new Date().toISOString().slice(0, 10);

  if (targetDate <= today) {
    throw new Error("목표 날짜는 내일 이후로 선택해 주세요.");
  }

  return targetDate;
}

function splitLines(value: string) {
  return value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

function getTextValue(formData: FormData, name: string) {
  const value = formData.get(name);
  return typeof value === "string" ? value : "";
}

function isCurrentLevel(value: string): value is CurrentLevel {
  return currentLevelOptions.includes(value as CurrentLevel);
}

function isProficiencyLevel(value: string): value is ProficiencyLevel {
  return proficiencyLevelOptions.includes(value as ProficiencyLevel);
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
