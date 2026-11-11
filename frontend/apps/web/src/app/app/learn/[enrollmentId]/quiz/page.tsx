import type { Metadata } from "next";
import { notFound } from "next/navigation";
import type { EnrollmentDetail } from "@lms/api-client";

import { ApiError, apiFetch } from "@/server/api";

import { QuizRunner } from "./quiz-runner";

export const metadata: Metadata = { title: "퀴즈" };

/** The course outline beside the quiz is a plain GET; starting the attempt stays a client POST. */
export default async function QuizPage({ params }: { params: Promise<{ enrollmentId: string }> }) {
  const { enrollmentId } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(enrollmentId)) notFound();

  let course: EnrollmentDetail | null = null;
  try {
    course = await apiFetch<EnrollmentDetail>(`/v1/my/enrollments/${enrollmentId}`);
  } catch (error) {
    // The quiz still works without the outline; the runner reports its own errors.
    if (!(error instanceof ApiError)) throw error;
  }
  return <QuizRunner enrollmentId={enrollmentId} course={course} />;
}
