package com.lms.learning.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The watch-time credit rule at its edges (docs/05 §8). */
class ProgressRulesTest {

    static final Instant T0 = Instant.parse("2026-11-06T01:00:00Z");

    @Test
    @DisplayName("정상 재생: 15초 동안 15초 이동하면 15초 모두 인정")
    void normalPlayback() {
        assertThat(ProgressRules.credit(100, T0, 115, T0.plusSeconds(15))).isEqualTo(15);
    }

    @Test
    @DisplayName("2배속은 인정, 그보다 빠른 이동은 (경과×2 + 5초)까지만")
    void doubleSpeedIsTheCeiling() {
        assertThat(ProgressRules.credit(0, T0, 30, T0.plusSeconds(15))).isEqualTo(30);
        assertThat(ProgressRules.credit(0, T0, 35, T0.plusSeconds(15))).isEqualTo(35);
        assertThat(ProgressRules.credit(0, T0, 36, T0.plusSeconds(15))).isEqualTo(35);
    }

    @Test
    @DisplayName("끝으로 건너뛰기: 1,800초를 15초 만에 이동해도 35초만 인정")
    void skippingToTheEnd() {
        assertThat(ProgressRules.credit(0, T0, 1800, T0.plusSeconds(15))).isEqualTo(35);
    }

    @Test
    @DisplayName("뒤로 감기는 인정 0 (위치만 갱신)")
    void seekingBack() {
        assertThat(ProgressRules.credit(600, T0, 300, T0.plusSeconds(15))).isZero();
        var outcome = ProgressRules.heartbeat(600, 600, T0, 300, T0.plusSeconds(15), 1000);
        assertThat(outcome.watchedSec()).isEqualTo(600);
        assertThat(outcome.positionSec()).isEqualTo(300);
    }

    @Test
    @DisplayName("30분 넘게 끊겼다가 오면 새 세션: 인정 0. 정확히 30분은 아직 같은 세션")
    void longGapStartsANewSession() {
        assertThat(ProgressRules.credit(0, T0, 20, T0.plusSeconds(30 * 60 + 1))).isZero();
        assertThat(ProgressRules.credit(0, T0, 20, T0.plusSeconds(30 * 60))).isEqualTo(20);
        assertThat(ProgressRules.credit(0, null, 20, T0)).isZero();
    }

    @Test
    @DisplayName("시계가 거꾸로 가면(음수 경과) 인정 0")
    void negativeElapsed() {
        assertThat(ProgressRules.credit(0, T0, 10, T0.minusSeconds(5))).isZero();
    }

    @Test
    @DisplayName("완료는 영상 길이의 90% 이상 (올림): 100초 영상은 90초부터, 61초 영상은 55초부터")
    void completionThreshold() {
        assertThat(ProgressRules.isComplete(89, 100)).isFalse();
        assertThat(ProgressRules.isComplete(90, 100)).isTrue();
        assertThat(ProgressRules.isComplete(54, 61)).isFalse();
        assertThat(ProgressRules.isComplete(55, 61)).isTrue();
        assertThat(ProgressRules.isComplete(0, 0)).isFalse();
    }

    @Test
    @DisplayName("인정 시간은 영상 길이를 넘지 않고, 위치도 0~길이로 자른다")
    void clampedToDuration() {
        var outcome = ProgressRules.heartbeat(95, 95, T0, 500, T0.plusSeconds(15), 100);
        assertThat(outcome.watchedSec()).isEqualTo(100);
        assertThat(outcome.positionSec()).isEqualTo(100);
        assertThat(outcome.step()).isEqualTo(4);
        assertThat(outcome.complete()).isTrue();
    }

    @Test
    @DisplayName("25% 단계: 24%→0, 25%→1, 74%→2, 75%→3")
    void steps() {
        assertThat(ProgressRules.step(24, 100)).isZero();
        assertThat(ProgressRules.step(25, 100)).isEqualTo(1);
        assertThat(ProgressRules.step(74, 100)).isEqualTo(2);
        assertThat(ProgressRules.step(75, 100)).isEqualTo(3);
    }
}
