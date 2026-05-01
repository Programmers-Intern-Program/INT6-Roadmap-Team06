type StatePanelProps = {
  className?: string;
  message: string;
  tone?: "danger" | "neutral";
};

export function StatePanel({
  className,
  message,
  tone = "neutral"
}: StatePanelProps) {
  return (
    <section
      className={["panel", className].filter(Boolean).join(" ")}
      data-tone={tone}
    >
      <p>{message}</p>
    </section>
  );
}
