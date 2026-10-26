package com.gathin.tvhome;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OledShiftTest {
    @Test public void offsetsStayWithinThreePixels() { Random r = new Random(7); int[] p = {0, 0}; for (int i = 0; i < 500; i++) { p = OledShift.nextOffset(r, p); assertTrue(Math.abs(p[0]) <= 3 && Math.abs(p[1]) <= 3); } }
    @Test public void neverRepeatsThePreviousPoint() { Random r = new Random(3); int[] p = {0, 0}; for (int i = 0; i < 500; i++) { int[] n = OledShift.nextOffset(r, p); assertFalse(n[0] == p[0] && n[1] == p[1]); p = n; } }
    /** Spec § 4.10a: ±3 real pixels (ruling R15 revised), so the offset is the translation, with no density on top. */
    @Test public void enabledMovesWithinThreePixels() {
        OledShift s = new OledShift(java.util.Collections.emptyList(), new Random(5), true);
        assertTrue(OledShift.MAX == 3);
        for (int i = 0; i < 100; i++) {
            s.next();
            int[] p = s.current();
            assertTrue(Math.abs(p[0]) <= 3 && Math.abs(p[1]) <= 3);
            assertFalse(p[0] == 0 && p[1] == 0 && i == 0);
        }
    }
    @Test public void disabledNeverMoves() { OledShift s = new OledShift(java.util.Collections.emptyList(), new Random(1), false); s.next(); assertArrayEquals(new int[]{0, 0}, s.current()); }
}
