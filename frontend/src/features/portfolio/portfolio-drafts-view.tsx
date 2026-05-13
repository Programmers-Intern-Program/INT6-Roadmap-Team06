"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import { StatePanel } from "@/components/state-panel";
import {
  createPortfolioDraft,
  generatePortfolioDraftVariant,
  getPortfolioDraft,
  listPortfolioDrafts,
  updatePortfolioDraft
} from "@/features/portfolio/api";
import type {
  PortfolioDraftDetail,
  PortfolioDraftPayload,
  PortfolioDraftSummary,
  PortfolioDraftVariant
} from "@/features/portfolio/types";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type ViewState =
  | { status: "loading" }
  | { status: "auth-required" }
  | { status: "ready" }
  | { status: "error"; message: string };

const variantLabels: Record<string, string> = {
  ALL: "전체 계획 포함",
  DONE: "완료 기반",
  DONE_IN_PROGRESS: "완료 + 진행 중"
};

const preferredVariantOrder = ["DONE", "DONE_IN_PROGRESS", "ALL"];

export function PortfolioDraftsView() {
  const [state, setState] = useState<ViewState>({ status: "loading" });
  const [drafts, setDrafts] = useState<PortfolioDraftSummary[]>([]);
  const [selectedDraft, setSelectedDraft] = useState<PortfolioDraftDetail | null>(null);
  const [draftPayload, setDraftPayload] = useState<PortfolioDraftPayload | null>(null);
  const [title, setTitle] = useState("");
  const [activeVariantKey, setActiveVariantKey] = useState("DONE");
  const [loadingDraftId, setLoadingDraftId] = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [generatingVariantKey, setGeneratingVariantKey] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const applyDetail = useCallback((draft: PortfolioDraftDetail, nextVariantKey?: string) => {
    setSelectedDraft(draft);
    setDraftPayload(draft.draftPayload);
    setTitle(draft.title);
    setActiveVariantKey(nextVariantKey ?? firstVariantKey(draft.draftPayload));
  }, []);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const summaries = await listPortfolioDrafts();
        if (cancelled) return;
        setDrafts(summaries);
        setState({ status: "ready" });

        if (summaries.length > 0) {
          const detail = await getPortfolioDraft(summaries[0].draftId);
          if (!cancelled) {
            applyDetail(detail);
          }
        }
      } catch (error) {
        if (cancelled) return;
        if (isUnauthorizedError(error)) {
          setState({ status: "auth-required" });
          return;
        }
        setState({
          status: "error",
          message: getErrorMessage(error, "포트폴리오 초안 목록을 불러오지 못했습니다.")
        });
      }
    }

    load();

    return () => {
      cancelled = true;
    };
  }, [applyDetail]);

  const activeVariant = useMemo(() => {
    return draftPayload?.variants.find((variant) => variant.key === activeVariantKey)
      ?? draftPayload?.variants[0]
      ?? null;
  }, [activeVariantKey, draftPayload]);
  const activeVariantGenerated = activeVariant ? isVariantGenerated(activeVariant) : false;

  async function handleSelectDraft(draftId: string) {
    if (selectedDraft?.draftId === draftId || loadingDraftId === draftId) {
      return;
    }
    setActionError(null);
    setLoadingDraftId(draftId);
    try {
      const detail = await getPortfolioDraft(draftId);
      applyDetail(detail);
    } catch (error) {
      if (isUnauthorizedError(error)) {
        setState({ status: "auth-required" });
        return;
      }
      setActionError(getErrorMessage(error, "초안을 불러오지 못했습니다."));
    } finally {
      setLoadingDraftId(null);
    }
  }

  async function handleGenerateDraft() {
    setActionError(null);
    setIsGenerating(true);
    try {
      const draft = await createPortfolioDraft();
      setDrafts((current) => [
        toSummary(draft),
        ...current.filter((item) => item.draftId !== draft.draftId)
      ]);
      applyDetail(draft);
    } catch (error) {
      if (isUnauthorizedError(error)) {
        setState({ status: "auth-required" });
        return;
      }
      setActionError(getErrorMessage(error, "초안을 생성하지 못했습니다."));
    } finally {
      setIsGenerating(false);
    }
  }

  async function handleGenerateVariant() {
    if (!selectedDraft || !activeVariant) {
      return;
    }
    setActionError(null);
    setGeneratingVariantKey(activeVariant.key);
    try {
      const saved = await generatePortfolioDraftVariant(selectedDraft.draftId, activeVariant.key);
      setDrafts((current) =>
        current.map((item) =>
          item.draftId === saved.draftId ? toSummary(saved) : item
        )
      );
      applyDetail(saved, activeVariant.key);
    } catch (error) {
      if (isUnauthorizedError(error)) {
        setState({ status: "auth-required" });
        return;
      }
      setActionError(getErrorMessage(error, "초안 버전을 생성하지 못했습니다."));
    } finally {
      setGeneratingVariantKey(null);
    }
  }

  async function handleSaveDraft() {
    if (!selectedDraft || !draftPayload) {
      return;
    }
    const trimmedTitle = title.trim();
    if (!trimmedTitle) {
      setActionError("초안 제목을 입력해 주세요.");
      return;
    }

    setActionError(null);
    setIsSaving(true);
    try {
      const saved = await updatePortfolioDraft(selectedDraft.draftId, {
        title: trimmedTitle,
        draftPayload
      });
      setDrafts((current) =>
        current.map((item) =>
          item.draftId === saved.draftId ? toSummary(saved) : item
        )
      );
      applyDetail(saved);
    } catch (error) {
      if (isUnauthorizedError(error)) {
        setState({ status: "auth-required" });
        return;
      }
      setActionError(getErrorMessage(error, "초안을 저장하지 못했습니다."));
    } finally {
      setIsSaving(false);
    }
  }

  function handleVariantContentChange(nextContent: string) {
    if (!activeVariant) return;
    setDraftPayload((current) => {
      if (!current) return current;
      return {
        ...current,
        variants: current.variants.map((variant) =>
          variant.key === activeVariant.key
            ? { ...variant, content: nextContent, generated: true }
            : variant
        )
      };
    });
  }

  function handleStartManualInput() {
    if (!activeVariant) return;
    setDraftPayload((current) => {
      if (!current) return current;
      return {
        ...current,
        variants: current.variants.map((variant) =>
          variant.key === activeVariant.key
            ? { ...variant, generated: true }
            : variant
        )
      };
    });
  }

  if (state.status === "loading") {
    return <StatePanel message="포트폴리오 초안을 불러오는 중입니다." />;
  }

  if (state.status === "auth-required") {
    return <AuthRequiredPanel redirectPath="/portfolio/drafts" />;
  }

  if (state.status === "error") {
    return <StatePanel message={state.message} tone="danger" />;
  }

  return (
    <section className="portfolio-drafts-page" aria-labelledby="portfolio-drafts-title">
      <header className="screen-hero">
        <p className="eyebrow">v1 확장</p>
        <div className="screen-heading">
          <h1 id="portfolio-drafts-title">포트폴리오 초안</h1>
          <p>
            로드맵 진도, GitHub 분석, Coach 대화 후보를 모아 프로젝트 기술서 초안을 만듭니다.
          </p>
        </div>
      </header>

      <section className="portfolio-draft-action-panel" aria-label="초안 생성">
        <div>
          <h2>최신 학습 기록으로 초안 생성</h2>
          <p>빈 초안 틀을 만든 뒤 필요한 버전만 탭별로 생성하고 편집합니다.</p>
        </div>
        <button
          className="action-link primary"
          disabled={isGenerating}
          type="button"
          onClick={handleGenerateDraft}
        >
          {isGenerating ? "생성 중" : "빈 초안 생성"}
        </button>
      </section>

      {actionError ? (
        <StatePanel className="portfolio-draft-state-panel" message={actionError} tone="danger" />
      ) : null}

      <div className="portfolio-drafts-layout">
        <aside className="portfolio-draft-list" aria-label="저장된 초안 목록">
          <header>
            <h2>저장된 초안</h2>
            <span>{drafts.length}개</span>
          </header>
          {drafts.length === 0 ? (
            <p className="portfolio-draft-empty">아직 저장된 초안이 없습니다.</p>
          ) : (
            <ul>
              {drafts.map((draft) => (
                <li key={draft.draftId}>
                  <button
                    type="button"
                    className="portfolio-draft-list-item"
                    data-active={selectedDraft?.draftId === draft.draftId ? "true" : undefined}
                    onClick={() => void handleSelectDraft(draft.draftId)}
                  >
                    <span>{draft.title}</span>
                    <small>
                      {loadingDraftId === draft.draftId
                        ? "불러오는 중"
                        : formatDateTime(draft.updatedAt)}
                    </small>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </aside>

        <section className="portfolio-draft-editor" aria-label="초안 편집">
          {selectedDraft && draftPayload && activeVariant ? (
            <>
              <div className="portfolio-draft-editor-header">
                <label>
                  <span>제목</span>
                  <input
                    maxLength={255}
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                  />
                </label>
                <dl className="portfolio-draft-source-summary">
                  <div>
                    <dt>로드맵</dt>
                    <dd>{countArray(selectedDraft.sourceRefs, "roadmaps")}</dd>
                  </div>
                  <div>
                    <dt>진도</dt>
                    <dd>{countArray(selectedDraft.sourceRefs, "progressLogIds")}</dd>
                  </div>
                  <div>
                    <dt>GitHub</dt>
                    <dd>{countArray(selectedDraft.sourceRefs, "githubAnalyses")}</dd>
                  </div>
                  <div>
                    <dt>Coach</dt>
                    <dd>{countArray(selectedDraft.sourceRefs, "coachConversationIds")}</dd>
                  </div>
                </dl>
              </div>

              <div className="portfolio-draft-tabs" role="tablist" aria-label="초안 버전">
                {sortedVariants(draftPayload.variants).map((variant) => (
                  <button
                    key={variant.key}
                    type="button"
                    role="tab"
                    aria-selected={activeVariant.key === variant.key}
                    data-active={activeVariant.key === variant.key ? "true" : undefined}
                    onClick={() => setActiveVariantKey(variant.key)}
                  >
                    {variantLabels[variant.key] ?? variant.label}
                  </button>
                ))}
              </div>

              {activeVariantGenerated ? (
                <textarea
                  className="portfolio-draft-textarea"
                  value={activeVariant.content}
                  onChange={(event) => handleVariantContentChange(event.target.value)}
                />
              ) : (
                <div className="portfolio-draft-empty-variant">
                  <p>아직 생성되지 않은 버전입니다.</p>
                  <div className="portfolio-draft-empty-actions">
                    <button
                      className="action-link primary"
                      disabled={generatingVariantKey === activeVariant.key}
                      type="button"
                      onClick={() => void handleGenerateVariant()}
                    >
                      {generatingVariantKey === activeVariant.key
                        ? "생성 중"
                        : `${variantLabels[activeVariant.key] ?? activeVariant.label} 생성`}
                    </button>
                    <button
                      className="action-link"
                      type="button"
                      onClick={handleStartManualInput}
                    >
                      직접 입력
                    </button>
                  </div>
                </div>
              )}

              <div className="portfolio-draft-editor-footer">
                <span>최근 저장 {formatDateTime(selectedDraft.updatedAt)}</span>
                <button
                  className="action-link primary"
                  disabled={isSaving}
                  type="button"
                  onClick={() => void handleSaveDraft()}
                >
                  {isSaving ? "저장 중" : "저장"}
                </button>
              </div>

              <details className="portfolio-draft-source-refs">
                <summary>사용 근거 ID</summary>
                <pre>{JSON.stringify(selectedDraft.sourceRefs, null, 2)}</pre>
              </details>
            </>
          ) : (
            <StatePanel
              className="portfolio-draft-state-panel"
              message="초안을 생성하거나 저장된 초안을 선택해 주세요."
            />
          )}
        </section>
      </div>
    </section>
  );
}

function firstVariantKey(payload: PortfolioDraftPayload) {
  const generatedKey = preferredVariantOrder.find((key) =>
    payload.variants.some((variant) => variant.key === key && isVariantGenerated(variant))
  );
  if (generatedKey) {
    return generatedKey;
  }
  return preferredVariantOrder.find((key) =>
    payload.variants.some((variant) => variant.key === key)
  ) ?? payload.variants[0]?.key ?? "DONE";
}

function isVariantGenerated(variant: PortfolioDraftVariant) {
  return variant.generated === true || variant.content.trim().length > 0;
}

function sortedVariants(variants: PortfolioDraftVariant[]) {
  return [...variants].sort((a, b) => {
    const ai = preferredVariantOrder.indexOf(a.key);
    const bi = preferredVariantOrder.indexOf(b.key);
    return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
  });
}

function toSummary(draft: PortfolioDraftDetail): PortfolioDraftSummary {
  return {
    createdAt: draft.createdAt,
    draftId: draft.draftId,
    title: draft.title,
    updatedAt: draft.updatedAt
  };
}

function countArray(value: unknown, key: string) {
  if (!isRecord(value)) return 0;
  const array = value[key];
  return Array.isArray(array) ? array.length : 0;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function getErrorMessage(error: unknown, fallbackMessage: string) {
  if (error instanceof ApiError) {
    return error.message;
  }

  return fallbackMessage;
}
