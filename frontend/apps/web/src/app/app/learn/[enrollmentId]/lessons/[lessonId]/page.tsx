import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { LessonPlayer } from "./lesson-player";

export const metadata: Metadata = { title: "레슨" };

/**
 * Opening a lesson has effects (the enrollment starts, a playback session starts, xAPI
 * 'launched'), so it is a POST made by the client component once it is on screen, not a GET
 * during server rendering (prefetching would otherwise "launch" lessons nobody opened).
 */
export default async function LessonPage({ params }: { params: Promise<{ enrollmentId: string; lessonId: string }> }) {
  const { enrollmentId, lessonId } = await params;
  const uuid = /^[0-9a-f-]{36}$/i;
  if (!uuid.test(enrollmentId) || !uuid.test(lessonId)) notFound();
  return <LessonPlayer key={lessonId} enrollmentId={enrollmentId} lessonId={lessonId} />;
}
