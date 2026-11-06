import { NextResponse, type NextRequest } from "next/server";
import type { CertificateLink } from "@lms/api-client";

import { ApiError, UnauthenticatedError, apiFetch } from "@/server/api";

/**
 * GET /app/certificates/{enrollmentId} → 302 to a 5-minute presigned S3 link for the PDF.
 * A link on the page instead of the S3 URL itself: the S3 link expires, this one does not.
 * While the worker is still rendering (409), go back to the course page with a notice.
 */
export async function GET(request: NextRequest, { params }: { params: Promise<{ enrollmentId: string }> }) {
  const { enrollmentId } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(enrollmentId)) return new NextResponse("Not found", { status: 404 });
  try {
    const link = await apiFetch<CertificateLink>(`/v1/my/enrollments/${enrollmentId}/certificate`);
    return NextResponse.redirect(link.url, 302);
  } catch (error) {
    if (error instanceof UnauthenticatedError) {
      return NextResponse.redirect(new URL(`/auth/login?returnTo=/app/learn/${enrollmentId}`, request.url));
    }
    if (error instanceof ApiError && error.status === 409) {
      return NextResponse.redirect(new URL(`/app/learn/${enrollmentId}?certificate=pending`, request.url));
    }
    if (error instanceof ApiError && error.status === 404) return new NextResponse("Not found", { status: 404 });
    throw error;
  }
}
