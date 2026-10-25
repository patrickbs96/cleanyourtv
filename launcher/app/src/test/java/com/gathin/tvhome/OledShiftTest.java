package com.gathin.tvhome;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OledShiftTest {
    @Test public void offsetsStayWithinThreePixels() { Random r = new Random(7); int[] p = {0, 0}; for (int i = 0; i < 500; i++) { p = OledShift.nextOffset(r, p); assertTrue(Math.abs(p[0]) <= 3 && Math.abs(p[1]) <= 3); } }
    @Test public void neverRepeatsThePreviousPoint() { Random r = new Random(3); int[] p = {0, 0}; for (int i = 0; i < 500; i++) { int[] n = OledShift.nextOffset(r, p); assertFalse(n[0] == p[0] && n[1] == p[1]); p = n; } }
    @Test public void disabledNeverMoves() { OledShift s = new OledShift(java.util.Collections.emptyList(), new Random(1), false); s.next(); assertArrayEquals(new int[]{0, 0}, s.current()); }
}
