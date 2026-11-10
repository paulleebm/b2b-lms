import type { Metadata } from "next";
import Link from "next/link";
import type { CertificateVerification } from "@lms/api-client";
import { CompletionMark, Panel } from "@lms/ui";

import { koreanDate } from "@/lib/learning";
import { getEnv } from "@/server/env";

export const metadata: Metadata = { title: "수료증 진위 확인", robots: { index: false } };

/**
 * Public page (no sign-in): anyone holding a certificate's serial — an auditor, a client — can
 * confirm it was issued. Only what a verifier needs is shown, with the holder's name masked.
 */
async function lookup(serial: string): Promise<CertificateVerification | null | "error"> {
  if (!/^LMS-\d{4}-[0-9A-HJKMNP-TV-Z]{8}$/.test(serial)) return null;
  try {
    const response = await fetch(`${getEnv().API_URL}/v1/public/certificates/${encodeURIComponent(serial)}`, {
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (response.status === 404) return null;
    if (!response.ok) return "error";
    return (await response.json()) as CertificateVerification;
  } catch {
    return "error";
  }
}

export default async function VerifyPage({ params }: { params: Promise<{ serial: string }> }) {
  const { serial } = await params;
  const result = await lookup(decodeURIComponent(serial));

  return (
    <main id="main" className="mx-auto grid min-h-dvh max-w-xl place-items-center px-6 py-12">
      <Panel className="w-full">
        {result === "error" ? (
          <div className="grid gap-2 p-8 text-center">
            <h1 className="text-title font-semibold text-ink">지금은 확인할 수 없습니다</h1>
            <p className="text-body-sm text-muted">잠시 후 다시 시도해 주세요.</p>
          </div>
        ) : result ? (
          <div className="flex flex-col items-center gap-5 p-8 text-center">
            <CompletionMark size="lg" completedAt={koreanDate(result.issuedAt)} />
            <div className="grid gap-1">
              <h1 className="text-title font-semibold text-ink">정상 발급된 수료증입니다</h1>
              <p className="text-body-sm text-muted tabular">수료번호 {result.serial}</p>
            </div>
            <dl className="grid w-full grid-cols-[6rem_1fr] gap-x-4 gap-y-2 text-left text-body-sm">
              <dt className="text-muted">회사</dt>
              <dd className="text-ink">{result.companyName}</dd>
              <dt className="text-muted">수료자</dt>
              <dd className="text-ink">{result.holderName}</dd>
              <dt className="text-muted">과정</dt>
              <dd className="text-ink">{result.courseTitle}</dd>
              <dt className="text-muted">수료일</dt>
              <dd className="text-ink">{koreanDate(result.issuedAt)}</dd>
            </dl>
            <p className="text-caption text-muted">개인정보 보호를 위해 수료자 이름의 일부를 가렸습니다.</p>
          </div>
        ) : (
          <div className="grid gap-2 p-8 text-center">
            <h1 className="text-title font-semibold text-ink">확인할 수 없는 수료번호입니다</h1>
            <p className="text-body-sm text-muted">수료증에 적힌 번호를 그대로 입력했는지 확인해 주세요.</p>
          </div>
        )}
      </Panel>
      <Link href="/" className="mt-6 text-label text-muted hover:text-ink">
        LMS
      </Link>
    </main>
  );
}
