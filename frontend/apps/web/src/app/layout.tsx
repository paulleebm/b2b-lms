import type { Metadata } from "next";

import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "LMS — 기업 교육 플랫폼",
    template: "%s · LMS",
  },
  description: "회사 단위로 직원 교육을 배정하고, 학습 진도와 수료를 관리하는 B2B 교육 플랫폼",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body className="min-h-dvh">{children}</body>
    </html>
  );
}
