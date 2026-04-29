const dashboardItems = [
  { label: "프로필", value: "준비 중", state: "active" },
  { label: "GitHub 분석", value: "대기" },
  { label: "진단", value: "대기" },
  { label: "로드맵", value: "대기" }
];

export default function Home() {
  return (
    <div className="app-frame">
      <header className="topbar">
        <span className="brand">AI Growth Coach</span>
        <span className="status">v1</span>
      </header>
      <main className="page">
        <h1 className="page-title">대시보드</h1>
        <section className="summary-grid" aria-label="현재 상태 요약">
          {dashboardItems.map((item) => (
            <article
              className="summary-item"
              data-state={item.state}
              key={item.label}
            >
              <p className="summary-label">{item.label}</p>
              <p className="summary-value">{item.value}</p>
            </article>
          ))}
        </section>
      </main>
    </div>
  );
}
