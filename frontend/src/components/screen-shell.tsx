import Link from "next/link";

import type { ScreenDefinition } from "@/config/routes";

type ScreenShellProps = {
  screen: ScreenDefinition;
};

export function ScreenShell({ screen }: ScreenShellProps) {
  return (
    <section className="screen-shell">
      <div className="screen-hero">
        <p className="eyebrow">{screen.eyebrow}</p>
        <div className="screen-heading">
          <h1>{screen.title}</h1>
          <p>{screen.description}</p>
        </div>
        <div className="action-row" aria-label="다음 행동">
          <Link className="action-link primary" href={screen.primaryAction.href}>
            {screen.primaryAction.label}
          </Link>
          {screen.secondaryAction ? (
            <Link className="action-link" href={screen.secondaryAction.href}>
              {screen.secondaryAction.label}
            </Link>
          ) : null}
        </div>
      </div>

      <div className="content-grid">
        <section className="panel" aria-labelledby="screen-states-title">
          <h2 id="screen-states-title">화면 상태</h2>
          <div className="state-list">
            {screen.states.map((state) => (
              <span className="state-chip" key={state}>
                {state}
              </span>
            ))}
          </div>
        </section>

        {screen.sections.map((section) => (
          <section className="panel" key={section.title}>
            <h2>{section.title}</h2>
            <ul className="item-list">
              {section.items.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </section>
  );
}
