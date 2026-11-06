package com.lms.shared;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562): 48-bit Unix millisecond timestamp + random bits.
 *
 * <ul>
 *   <li>Not guessable like 1, 2, 3 ids, so nobody can walk {@code /courses/1..N} (IDOR).</li>
 *   <li>Time-ordered, so new rows land at the end of the B-tree index (v4 scatters inserts),
 *       and ids double as stable cursors for pagination.</li>
 * </ul>
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        byte[] value = new byte[16];
        RANDOM.nextBytes(value);

        long millis = System.currentTimeMillis();
        value[0] = (byte) (millis >>> 40);
        value[1] = (byte) (millis >>> 32);
        value[2] = (byte) (millis >>> 24);
        value[3] = (byte) (millis >>> 16);
        value[4] = (byte) (millis >>> 8);
        value[5] = (byte) millis;

        value[6] = (byte) ((value[6] & 0x0F) | 0x70); // version 7
        value[8] = (byte) ((value[8] & 0x3F) | 0x80); // IETF variant

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (value[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (value[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
