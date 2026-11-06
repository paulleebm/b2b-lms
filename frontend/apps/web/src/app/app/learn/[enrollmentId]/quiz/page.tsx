import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { QuizRunner } from "./quiz-runner";

export const metadata: Metadata = { title: "퀴즈" };

export default async function QuizPage({ params }: { params: Promise<{ enrollmentId: string }> }) {
  const { enrollmentId } = await params;
  if (!/^[0-9a-f-]{36}$/i.test(enrollmentId)) notFound();
  return <QuizRunner enrollmentId={enrollmentId} />;
}
