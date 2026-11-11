import type { Metadata } from "next";
import { notFound } from "next/navigation";
import type { EnrollmentDetail } from "@lms/api-client";

import { ApiError, apiFetch } from "@/server/api";

import { LessonPlayer } from "./lesson-player";

export const metadata: Metadata = { title: "레슨" };

/**
 * Opening a lesson has effects (the enrollment starts, a playback session starts, xAPI
 * 'launched'), so it is a POST made by the client component once it is on screen, not a GET
 * during server rendering (prefetching would otherwise "launch" lessons nobody opened).
 * The course outline beside it is a plain GET: reading it changes nothing.
 */
export default async function LessonPage({ params }: { params: Promise<{ enrollmentId: string; lessonId: string }> }) {
  const { enrollmentId, lessonId } = await params;
  const uuid = /^[0-9a-f-]{36}$/i;
  if (!uuid.test(enrollmentId) || !uuid.test(lessonId)) notFound();

  let course: EnrollmentDetail | null = null;
  try {
    course = await apiFetch<EnrollmentDetail>(`/v1/my/enrollments/${enrollmentId}`);
  } catch (error) {
    // The lesson still works without the outline; the player reports its own errors.
    if (!(error instanceof ApiError)) throw error;
  }
  return <LessonPlayer key={lessonId} enrollmentId={enrollmentId} lessonId={lessonId} course={course} />;
}
