package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Plain JVM tests for the company's night back light (CompanyRim): it comes from the whole figure's mask, so only its
 * outer contour lights up, never the seams between its parts, and it lies outside the figure, which stays dark.
 */
public class CompanyRimTest {
    private static final int W = 60, H = 50;

    /** Two overlapping parts (a body and a head, a horse and its rider) make one mask: no line where they meet. */
    @Test public void onlyTheOuterContourLights() {
        byte[] mask = new byte[W * H];
        fill(mask, 20, 15, 35, 40);   // the body
        fill(mask, 30, 8, 42, 22);    // the head, overlapping it
        byte[] rim = CompanyRim.outline(mask, W, H, 2, 5, 0.3f, 2, 2);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int m = mask[y * W + x] & 0xFF, r = rim[y * W + x] & 0xFF;
                // Inside the figure, the seam between the parts included: nothing, the body stays dark.
                if (m == 255) assertEquals(x + "," + y, 0, r);
            }
        }
        // Just outside the contour the edge is full, on every side.
        assertEquals(255, at(rim, 19, 30));
        assertEquals(255, at(rim, 36, 30));
        assertEquals(255, at(rim, 43, 12));
        assertEquals(255, at(rim, 25, 41));
        // Further out only the faint glow, and far off nothing.
        int glow = at(rim, 16, 30);
        assertTrue("" + glow, glow > 0 && glow <= Math.round(255 * 0.3f));
        assertEquals(0, at(rim, 2, 2));
    }

    /** The glow leans towards the crater on the left: a little stronger left of the figure than right of it. */
    @Test public void glowLeansTowardsTheCrater() {
        byte[] mask = new byte[W * H];
        fill(mask, 25, 15, 35, 40);
        byte[] rim = CompanyRim.outline(mask, W, H, 2, 5, 0.3f, 2, 2);
        // Four pixels off the square on either side.
        assertTrue(at(rim, 21, 28) > at(rim, 38, 28));
        assertTrue(at(rim, 30, 11) > at(rim, 30, 43));
        // Without a lean both sides are alike.
        byte[] even = CompanyRim.outline(mask, W, H, 2, 5, 0.3f, 0, 0);
        assertEquals(at(even, 21, 28), at(even, 38, 28));
        assertEquals(at(even, 30, 11), at(even, 30, 43));
    }

    /** A soft mask edge (anti-aliasing) gets its share of the rim, the opaque inside none. */
    @Test public void aSoftEdgeGetsPartOfTheRim() {
        byte[] mask = new byte[W * H];
        fill(mask, 20, 20, 30, 30);
        mask[25 * W + 30] = (byte) 128;   // half covered just right of the square
        byte[] rim = CompanyRim.outline(mask, W, H, 2, 5, 0.3f, 0, 0);
        int r = at(rim, 30, 25);
        assertTrue("" + r, r > 100 && r < 160);
    }

    private static void fill(byte[] mask, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) mask[y * W + x] = (byte) 255;
    }

    private static int at(byte[] a, int x, int y) {
        return a[y * W + x] & 0xFF;
    }
}
