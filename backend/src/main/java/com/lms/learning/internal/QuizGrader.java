package com.lms.learning.internal;

import com.lms.catalog.CourseCatalog.KeyedQuestion;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side grading (the answer key never reaches the browser). Pure function.
 * A question scores its points only for exactly the right set of options: no partial credit on
 * multiple choice, so guessing "all of them" earns nothing.
 */
final class QuizGrader {

    private QuizGrader() {
    }

    record QuestionResult(UUID questionId, List<String> selected, boolean correct, int points) {
    }

    /** @param score percent of points, rounded half up (0–100) */
    record Result(int earned, int total, int score, boolean passed, List<QuestionResult> questions) {
    }

    static Result grade(List<KeyedQuestion> questions, Map<UUID, List<String>> answers, int passScore) {
        int earned = 0;
        int total = 0;
        List<QuestionResult> results = new ArrayList<>();
        for (KeyedQuestion q : questions) {
            total += q.points();
            List<String> selected = answers.getOrDefault(q.id(), List.of());
            Set<String> chosen = new HashSet<>(selected);
            boolean wellFormed = !"SINGLE".equals(q.type()) || chosen.size() == 1;
            boolean correct = wellFormed && chosen.equals(new HashSet<>(q.answer()));
            if (correct) {
                earned += q.points();
            }
            results.add(new QuestionResult(q.id(), List.copyOf(selected), correct, correct ? q.points() : 0));
        }
        int score = total == 0 ? 0 : (int) Math.round(earned * 100.0 / total);
        return new Result(earned, total, score, score >= passScore, results);
    }

    /** A submission after the time limit: kept on record, scored zero. */
    static Result expired(List<KeyedQuestion> questions, Map<UUID, List<String>> answers) {
        List<QuestionResult> results = questions.stream()
                .map(q -> new QuestionResult(q.id(), List.copyOf(answers.getOrDefault(q.id(), List.of())), false, 0))
                .toList();
        int total = questions.stream().mapToInt(KeyedQuestion::points).sum();
        return new Result(0, total, 0, false, results);
    }
}
