import type { Metadata } from "next";
import { Toaster } from "sonner";

import { AppShell } from "@/components/app-shell";
import { GithubAnalysisJobProvider } from "@/features/github-connection/github-analysis-job-context";
import "./globals.css";

export const metadata: Metadata = {
  title: "AI Growth Coach",
  description: "AI developer growth coach frontend"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>
        <GithubAnalysisJobProvider>
          <AppShell>{children}</AppShell>
        </GithubAnalysisJobProvider>
        <Toaster position="top-right" richColors closeButton />
      </body>
    </html>
  );
}
