import type { ReactNode } from "react";

export type StatusBadgeTone =
  | "danger"
  | "info"
  | "neutral"
  | "success"
  | "warning";

type StatusBadgeProps = {
  children: ReactNode;
  className?: string;
  tone: StatusBadgeTone;
};

export function StatusBadge({
  children,
  className,
  tone
}: StatusBadgeProps) {
  const classes = ["status-badge", className].filter(Boolean).join(" ");

  return (
    <span className={classes} data-tone={tone}>
      {children}
    </span>
  );
}
