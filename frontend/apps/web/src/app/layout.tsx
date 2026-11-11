import type { Metadata, Viewport } from "next";

import "./globals.css";

const title = "LMS — 기업 교육 플랫폼";
const description = "회사 단위로 직원 교육을 배정하고, 학습 진도와 수료를 관리하는 B2B 교육 플랫폼";

// Link previews (KakaoTalk, Slack, X) need absolute URLs for og:image. The server always has APP_URL;
// getEnv() is not used here because the module is also evaluated during `next build`, where the
// other secrets are absent, so only pages prerendered at build time fall back to the public origin.
// The share image, Apple touch icon and manifest are file conventions in this folder; favicon.ico and
// the manifest icons live in public/. Sources and the regenerate command: DESIGN.md §11.
export const metadata: Metadata = {
  metadataBase: new URL(process.env.APP_URL ?? "https://app.bymile.site"),
  title: {
    default: title,
    template: "%s · LMS",
  },
  description,
  openGraph: {
    type: "website",
    siteName: "LMS",
    locale: "ko_KR",
    title,
    description,
  },
  twitter: { card: "summary_large_image" },
};

// Mobile address bar: the page background (--paper), so the bar blends into the landing header.
export const viewport: Viewport = {
  themeColor: "#f7f7f4",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body className="min-h-dvh">{children}</body>
    </html>
  );
}
