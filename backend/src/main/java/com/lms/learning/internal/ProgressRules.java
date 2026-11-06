package com.lms.learning.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * How much of a heartbeat counts as watching (docs/05 §8). Pure function, unit-tested at the edges.
 *
 * <pre>
 * elapsed  = now - lastHeartbeat
 * moved    = position - lastPosition
 * credited = clamp(moved, 0, elapsed × 2.0 + 5 s)   ← 2× playback speed + network slack
 * </pre>
 * Skipping to the end in one jump earns at most ~35 s; seeking back earns nothing (and only moves
 * the resume point); a heartbeat after more than 30 minutes of silence starts a new session and
 * earns nothing. The duration is the server's value, never the browser's.
 *
 * <p>Honest limit: a modified client can forge heartbeats. This stops the easy shortcut and raises
 * the cost of cheating; it is not proof of attention.
 */
final class ProgressRules {

    static final double MAX_SPEED = 2.0;
    static final int SLACK_SEC = 5;
    static final Duration SESSION_GAP = Duration.ofMinutes(30);
    static final double COMPLETE_RATIO = 0.9;

    private ProgressRules() {
    }

    /**
     * @param step highest 25%-step reached (0–4): 1 = 25 %, 2 = 50 %, 3 = 75 %, 4 = 100 %
     */
    record Outcome(int watchedSec, int positionSec, int creditedSec, int step, boolean complete) {
    }

    static Outcome heartbeat(int watchedSec, int lastPositionSec, Instant lastHeartbeatAt,
                             int positionSec, Instant now, int durationSec) {
        int position = Math.max(0, Math.min(positionSec, durationSec));
        int credited = credit(lastPositionSec, lastHeartbeatAt, position, now);
        int watched = Math.min(durationSec, watchedSec + credited);
        return new Outcome(watched, position, credited, step(watched, durationSec), isComplete(watched, durationSec));
    }

    static int credit(int lastPositionSec, Instant lastHeartbeatAt, int positionSec, Instant now) {
        if (lastHeartbeatAt == null) {
            return 0;
        }
        Duration gap = Duration.between(lastHeartbeatAt, now);
        if (gap.isNegative() || gap.compareTo(SESSION_GAP) > 0) {
            return 0;
        }
        long elapsedSec = gap.toSeconds();
        long moved = (long) positionSec - lastPositionSec;
        long cap = (long) Math.floor(elapsedSec * MAX_SPEED) + SLACK_SEC;
        return (int) Math.max(0, Math.min(moved, cap));
    }

    static int step(int watchedSec, int durationSec) {
        if (durationSec <= 0) {
            return 0;
        }
        return (int) Math.min(4, (long) watchedSec * 4 / durationSec);
    }

    static boolean isComplete(int watchedSec, int durationSec) {
        return durationSec > 0 && watchedSec >= Math.ceil(durationSec * COMPLETE_RATIO);
    }
}
