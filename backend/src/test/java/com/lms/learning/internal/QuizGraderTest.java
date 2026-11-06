package com.lms.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.catalog.CourseCatalog.KeyedQuestion;
import com.lms.catalog.CourseCatalog.Option;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizGraderTest {

    static final List<Option> ABCD = List.of(new Option("a", "1"), new Option("b", "2"), new Option("c", "3"), new Option("d", "4"));
    final UUID q1 = UUID.randomUUID();
    final UUID q2 = UUID.randomUUID();
    final UUID q3 = UUID.randomUUID();

    final List<KeyedQuestion> quiz = List.of(
            new KeyedQuestion(q1, "SINGLE", "단일", ABCD, List.of("b"), 40),
            new KeyedQuestion(q2, "MULTIPLE", "복수", ABCD, List.of("a", "c"), 40),
            new KeyedQuestion(q3, "SINGLE", "단일2", ABCD, List.of("d"), 20));

    @Test
    @DisplayName("전부 맞으면 100점, 통과")
    void allCorrect() {
        var r = QuizGrader.grade(quiz, Map.of(q1, List.of("b"), q2, List.of("c", "a"), q3, List.of("d")), 80);
        assertThat(r.score()).isEqualTo(100);
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("통과 기준과 같은 점수면 통과, 1점 모자라면 불합격 (경계값)")
    void passScoreBoundary() {
        var eighty = QuizGrader.grade(quiz, Map.of(q1, List.of("b"), q2, List.of("a", "c")), 80);
        assertThat(eighty.score()).isEqualTo(80);
        assertThat(eighty.passed()).isTrue();
        assertThat(QuizGrader.grade(quiz, Map.of(q1, List.of("b"), q2, List.of("a", "c")), 81).passed()).isFalse();
    }

    @Test
    @DisplayName("복수 선택은 정확히 맞아야 점수: 일부만 고르거나 전부 고르면 0점")
    void multipleChoiceIsAllOrNothing() {
        assertThat(QuizGrader.grade(quiz, Map.of(q2, List.of("a")), 50).questions().get(1).correct()).isFalse();
        assertThat(QuizGrader.grade(quiz, Map.of(q2, List.of("a", "b", "c", "d")), 50).questions().get(1).correct()).isFalse();
    }

    @Test
    @DisplayName("단일 선택에 두 개를 고르면 정답이 섞여 있어도 오답")
    void singleChoiceWithTwoAnswersIsWrong() {
        assertThat(QuizGrader.grade(quiz, Map.of(q1, List.of("a", "b")), 50).questions().get(0).correct()).isFalse();
    }

    @Test
    @DisplayName("답하지 않은 문항, 모르는 문항 ID는 오답 처리되고 점수에 영향 없음")
    void missingAndUnknownAnswers() {
        var r = QuizGrader.grade(quiz, Map.of(UUID.randomUUID(), List.of("a"), q3, List.of("d")), 50);
        assertThat(r.earned()).isEqualTo(20);
        assertThat(r.score()).isEqualTo(20);
    }

    @Test
    @DisplayName("점수는 반올림: 2/3 → 67")
    void rounding() {
        List<KeyedQuestion> three = List.of(
                new KeyedQuestion(q1, "SINGLE", "1", ABCD, List.of("a"), 1),
                new KeyedQuestion(q2, "SINGLE", "2", ABCD, List.of("a"), 1),
                new KeyedQuestion(q3, "SINGLE", "3", ABCD, List.of("a"), 1));
        assertThat(QuizGrader.grade(three, Map.of(q1, List.of("a"), q2, List.of("a")), 67).score()).isEqualTo(67);
    }

    @Test
    @DisplayName("시간 초과 제출은 답과 상관없이 0점, 불합격 (답은 기록에 남긴다)")
    void expiredScoresZero() {
        var r = QuizGrader.expired(quiz, Map.of(q1, List.of("b")));
        assertThat(r.score()).isZero();
        assertThat(r.passed()).isFalse();
        assertThat(r.questions().get(0).selected()).containsExactly("b");
    }
}
