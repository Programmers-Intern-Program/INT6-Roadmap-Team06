import type { ReactNode } from "react";
import "./globals.css";

export const metadata = {
  title: "Coach (dev)",
  description: "Auth smoke-test frontend",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <main style={{ maxWidth: 640, margin: "40px auto", padding: "0 16px", fontFamily: "system-ui, sans-serif" }}>
          {children}
        </main>
      </body>
    </html>
  );
}
