"use client";

import { useRouter } from "next/navigation";
import {
  type KeyboardEvent,
  type UIEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState
} from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import TextareaAutosize from "react-textarea-autosize";
import { toast } from "sonner";

import { AuthRequiredPanel } from "@/components/auth-required-panel";
import {
  closeSession,
  confirmReplan,
  sendMessage
} from "@/features/coach/api";
import {
  classifyCoachError,
  isAbortError
} from "@/features/coach/coach-errors";
import type {
  ChatBubble,
  CoachMessageResponse,
  ReplanProposal
} from "@/features/coach/types";
import { ApiError } from "@/lib/api";
import { isUnauthorizedError } from "@/lib/auth";

type ChatViewProps = {
  sessionId: string;
};

const SCROLL_STICK_THRESHOLD_PX = 80;

export function CoachChatView({ sessionId }: ChatViewProps) {
  const router = useRouter();
  const [bubbles, setBubbles] = useState<ChatBubble[]>([]);
  const [input, setInput] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [authRequired, setAuthRequired] = useState(false);
  const [sessionClosed, setSessionClosed] = useState(false);
  const [showScrollToBottom, setShowScrollToBottom] = useState(false);

  const abortRef = useRef<AbortController | null>(null);
  const scrollerRef = useRef<HTMLDivElement | null>(null);
  const stickToBottomRef = useRef(true);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);

  const handleScroll = useCallback((event: UIEvent<HTMLDivElement>) => {
    const el = event.currentTarget;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const stick = distanceFromBottom < SCROLL_STICK_THRESHOLD_PX;
    stickToBottomRef.current = stick;
    setShowScrollToBottom(!stick);
  }, []);

  const scrollToBottom = useCallback((behavior: ScrollBehavior = "smooth") => {
    const el = scrollerRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior });
    stickToBottomRef.current = true;
    setShowScrollToBottom(false);
  }, []);

  useEffect(() => {
    if (stickToBottomRef.current) {
      scrollToBottom("auto");
    }
  }, [bubbles, scrollToBottom]);

  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const handleAbort = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  const handleSend = useCallback(async () => {
    const trimmed = input.trim();
    if (!trimmed || isSending) return;

    const userBubbleId = `u-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const coachBubbleId = `c-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const idempotencyKey =
      typeof crypto !== "undefined" && "randomUUID" in crypto
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random()}`;

    setBubbles((prev) => [
      ...prev,
      { id: userBubbleId, role: "USER", text: trimmed },
      { id: coachBubbleId, role: "COACH", text: "", pending: true }
    ]);

    const controller = new AbortController();
    abortRef.current = controller;
    setIsSending(true);

    const slowToastId = `coach-slow-${userBubbleId}`;
    const slowTimer = window.setTimeout(() => {
      toast.message("응답이 평소보다 오래 걸리고 있어요.", {
        description: "잠시만 기다려 주세요. 중지하려면 '중지' 버튼을 눌러주세요.",
        id: slowToastId,
        duration: 8000
      });
    }, 10000);

    try {
      const response: CoachMessageResponse = await sendMessage(
        sessionId,
        trimmed,
        { signal: controller.signal, idempotencyKey }
      );

      setBubbles((prev) =>
        prev.map((b) =>
          b.id === coachBubbleId
            ? {
                ...b,
                text: response.responseText,
                route: response.route,
                replanProposal: response.replanProposal,
                pending: false
              }
            : b
        )
      );
      setInput("");
    } catch (error) {
      if (isAbortError(error)) {
        setBubbles((prev) => prev.filter((b) => b.id !== coachBubbleId));
        return;
      }

      setBubbles((prev) => prev.filter((b) => b.id !== coachBubbleId));

      if (isUnauthorizedError(error)) {
        setAuthRequired(true);
        return;
      }

      if (error instanceof ApiError && error.code === "SESSION_CLOSED") {
        setSessionClosed(true);
        toast.error("이미 종료된 세션입니다.", {
          description: "새 세션을 시작해 주세요."
        });
        return;
      }

      const info = classifyCoachError(error);
      toast.error(info.title, {
        description: info.detail,
        id: `coach-send-${userBubbleId}`
      });
    } finally {
      window.clearTimeout(slowTimer);
      toast.dismiss(slowToastId);
      if (abortRef.current === controller) {
        abortRef.current = null;
      }
      setIsSending(false);
    }
  }, [input, isSending, sessionId]);

  const handleNewSession = useCallback(async () => {
    abortRef.current?.abort();
    try {
      await closeSession(sessionId);
    } catch (error) {
      if (isUnauthorizedError(error)) {
        setAuthRequired(true);
        return;
      }
      const info = classifyCoachError(error);
      toast.error(info.title, { description: info.detail });
    }
    router.replace("/coach");
  }, [router, sessionId]);

  const handleConfirmReplan = useCallback(
    async (proposal: ReplanProposal, confirmed: boolean) => {
      try {
        const result = await confirmReplan(proposal.proposalId, confirmed);
        toast.success(result.message ?? (confirmed ? "재계획이 적용되었습니다." : "제안을 보류했어요."));
        if (confirmed && !result.dismissed) {
          await handleNewSession();
        }
      } catch (error) {
        if (isUnauthorizedError(error)) {
          setAuthRequired(true);
          return;
        }
        const info = classifyCoachError(error);
        toast.error(info.title, { description: info.detail });
      }
    },
    [handleNewSession]
  );

  const handleKeyDown = useCallback(
    (event: KeyboardEvent<HTMLTextAreaElement>) => {
      if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
        event.preventDefault();
        void handleSend();
        return;
      }
      if (event.key === "Escape") {
        if (isSending) {
          event.preventDefault();
          handleAbort();
        } else {
          inputRef.current?.blur();
        }
      }
    },
    [handleAbort, handleSend, isSending]
  );

  if (authRequired) {
    return (
      <AuthRequiredPanel redirectPath={`/coach/sessions/${sessionId}`} />
    );
  }

  if (sessionClosed) {
    return (
      <section className="screen-shell coach-screen">
        <div className="panel" data-tone="danger">
          <p>이미 종료된 세션입니다. 새 세션을 시작해 주세요.</p>
        </div>
        <div className="action-row">
          <button
            className="action-link primary"
            type="button"
            onClick={() => router.replace("/coach")}
          >
            새 세션 시작
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="coach-screen">
      <header className="coach-header">
        <h2 className="coach-title">코치</h2>
        <button
          type="button"
          className="coach-secondary-button"
          onClick={handleNewSession}
        >
          새 세션 시작
        </button>
      </header>

      <div
        className="coach-scroller"
        ref={scrollerRef}
        onScroll={handleScroll}
      >
        {bubbles.length === 0 ? (
          <div className="coach-empty">
            <p>무엇이 궁금한가요? 학습 진행 상황·로드맵 조정·막힌 부분을 자유롭게 말해 보세요.</p>
          </div>
        ) : null}

        <ul className="coach-bubbles" aria-live="polite">
          {bubbles.map((bubble) => (
            <li
              key={bubble.id}
              className={`coach-bubble coach-bubble-${bubble.role.toLowerCase()}`}
              data-pending={bubble.pending ? "true" : undefined}
            >
              {bubble.role === "COACH" ? (
                bubble.pending ? (
                  <div className="coach-typing" aria-label="코치가 응답을 작성 중입니다">
                    <span />
                    <span />
                    <span />
                  </div>
                ) : (
                  <>
                    <div className="coach-bubble-body">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {bubble.text}
                      </ReactMarkdown>
                    </div>
                    {bubble.replanProposal ? (
                      <ReplanCard
                        proposal={bubble.replanProposal}
                        onConfirm={(confirmed) =>
                          handleConfirmReplan(bubble.replanProposal!, confirmed)
                        }
                      />
                    ) : null}
                  </>
                )
              ) : (
                <div className="coach-bubble-body">{bubble.text}</div>
              )}
            </li>
          ))}
        </ul>

        {showScrollToBottom ? (
          <button
            type="button"
            className="coach-scroll-bottom"
            onClick={() => scrollToBottom("smooth")}
            aria-label="맨 아래로 이동"
          >
            ↓ 새 메시지
          </button>
        ) : null}
      </div>

      <form
        className="coach-composer"
        onSubmit={(event) => {
          event.preventDefault();
          void handleSend();
        }}
      >
        <TextareaAutosize
          ref={inputRef}
          className="coach-input"
          minRows={1}
          maxRows={8}
          placeholder="메시지를 입력하세요. Enter로 전송, Shift+Enter로 줄바꿈."
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={handleKeyDown}
          disabled={isSending}
        />
        {isSending ? (
          <button
            type="button"
            className="coach-stop-button"
            onClick={handleAbort}
          >
            중지
          </button>
        ) : (
          <button
            type="submit"
            className="coach-send-button"
            disabled={input.trim().length === 0}
          >
            전송
          </button>
        )}
      </form>
    </section>
  );
}

type ReplanCardProps = {
  proposal: ReplanProposal;
  onConfirm: (confirmed: boolean) => void | Promise<void>;
};

function ReplanCard({ proposal, onConfirm }: ReplanCardProps) {
  const expiresAtMs = useMemo(
    () => Date.parse(proposal.expiresAt),
    [proposal.expiresAt]
  );
  const [now, setNow] = useState(() => Date.now());
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, []);

  const remainingMs = Math.max(0, expiresAtMs - now);
  const expired = remainingMs === 0;
  const remainingLabel = formatRemaining(remainingMs);

  const handleClick = useCallback(
    async (confirmed: boolean) => {
      if (expired || submitting) return;
      setSubmitting(true);
      try {
        await onConfirm(confirmed);
      } finally {
        setSubmitting(false);
      }
    },
    [expired, onConfirm, submitting]
  );

  return (
    <aside className="coach-replan-card" data-expired={expired ? "true" : undefined}>
      <header className="coach-replan-header">
        <strong>로드맵 재계획 제안</strong>
        <span className="coach-replan-timer">
          {expired ? "만료됨" : `남은 시간 ${remainingLabel}`}
        </span>
      </header>
      <p className="coach-replan-reason">{proposal.reason}</p>
      <div className="coach-replan-actions">
        <button
          type="button"
          className="action-link primary"
          onClick={() => handleClick(true)}
          disabled={expired || submitting}
        >
          재계획 적용
        </button>
        <button
          type="button"
          className="action-link"
          onClick={() => handleClick(false)}
          disabled={expired || submitting}
        >
          이번엔 보류
        </button>
      </div>
    </aside>
  );
}

function formatRemaining(ms: number) {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes >= 1) {
    return `${minutes}분 ${String(seconds).padStart(2, "0")}초`;
  }
  return `${seconds}초`;
}
