package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Plain JVM tests for Middle-earth's walking company (spec § 4.2): dimmed to a quarter behind the focused app's name
 * (its extent plus 4 % of the width, softened over 3 %), full elsewhere and without a name; once across in about
 * 6 minutes, then a pause of 2 to 5 minutes fixed per day; and it goes on where it stood after a pause of Home.
 */
public class CompanyMotionTest {
    private static final float W = 1920f;

    /** Behind the name and its margin of 4 % of the width the company shows at 25 %. */
    @Test public void alphaInsideNameIsQuarter() {
        float left = 136f, right = 520f, margin = 0.04f * W, fade = 0.03f * W;
        for (float x = left - margin; x <= right + margin; x += 1f) {
            assertEquals(x + "", 0.25f, CompanyMotion.alphaAt(x, left, right, W), 1e-6f);
        }
        // Full again past the soft edge, on both sides, and in between it only rises going outwards.
        assertEquals(1f, CompanyMotion.alphaAt(right + margin + fade + 1f, left, right, W), 1e-6f);
        assertEquals(1f, CompanyMotion.alphaAt(left - margin - fade - 1f, left, right, W), 1e-6f);
        float before = 0.25f;
        for (float x = right + margin; x <= right + margin + fade; x += 1f) {
            float a = CompanyMotion.alphaAt(x, left, right, W);
            assertTrue(x + ": " + a, a >= before - 1e-6f && a <= 1f);
            before = a;
        }
        float mid = CompanyMotion.alphaAt(right + margin + fade / 2f, left, right, W);
        assertTrue("" + mid, mid > 0.4f && mid < 0.85f);
        // The edge is soft: no step between two neighbouring pixels larger than a tenth.
        for (float x = 0f; x < W; x += 1f) {
            float step = CompanyMotion.alphaAt(x + 1f, left, right, W) - CompanyMotion.alphaAt(x, left, right, W);
            assertTrue(x + ": " + step, Math.abs(step) < 0.1f);
        }
    }

    /** Without a name over the dock (nameLeft = nameRight = -1) the company walks at full strength everywhere. */
    @Test public void alphaWithoutHeroIsFull() {
        for (float x = -200f; x <= W + 200f; x += 3f) {
            assertEquals(x + "", 1f, CompanyMotion.alphaAt(x, -1f, -1f, W), 0f);
        }
    }

    /** The dimmed area follows the focused name: a long name dims further right than a short one. */
    @Test public void alphaFollowsLongName() {
        float left = 136f, shortRight = 520f, longRight = 1300f;
        float x = 1200f;
        assertEquals(1f, CompanyMotion.alphaAt(x, left, shortRight, W), 1e-6f);
        assertEquals(0.25f, CompanyMotion.alphaAt(x, left, longRight, W), 1e-6f);
        assertEquals(0.25f, CompanyMotion.alphaAt(longRight + 0.04f * W, left, longRight, W), 1e-6f);
        assertEquals(1f, CompanyMotion.alphaAt(longRight + 0.07f * W + 1f, left, longRight, W), 1e-6f);
        // Moved along the dock (another app focused, a name further right): the area moves with it.
        assertEquals(1f, CompanyMotion.alphaAt(300f, 900f, 1300f, W), 1e-6f);
        assertEquals(0.25f, CompanyMotion.alphaAt(1000f, 900f, 1300f, W), 1e-6f);
    }

    /** Stopped (onPause, a screensaver) and started again later, the company stands where it stood and walks on. */
    @Test public void companyResumesWhereItStopped() {
        CompanyMotion m = new CompanyMotion(20_000L);
        m.start(5_000);
        for (long t = 5_000; t <= 95_000; t += WorldOverlay.FRAME_MS) m.advance(t);
        // Stopped at 95 s: it walked 90 s, a quarter of the way.
        m.stop(95_000);
        float before = m.progress();
        assertEquals(90_000f / CompanyMotion.CROSS_MS, before, 1e-6f);
        // Home away for twenty minutes: nothing walked meanwhile.
        long back = 95_000 + 20 * 60_000;
        m.advance(back - 1);
        assertEquals(before, m.progress(), 0f);
        m.start(back);
        assertEquals(before, m.progress(), 0f);
        m.advance(back + WorldOverlay.FRAME_MS);
        assertEquals(before + WorldOverlay.FRAME_MS / (float) CompanyMotion.CROSS_MS, m.progress(), 1e-5f);
        // A frame that comes late (the UI thread busy for 3 s) moves it on a little only, it never jumps.
        float now = m.progress();
        m.advance(back + WorldOverlay.FRAME_MS + 3_000);
        assertTrue(m.progress() - now <= CompanyMotion.MAX_FRAME_MS / (float) CompanyMotion.CROSS_MS + 1e-6f);
        // A frame without a start (another overlay's after a recreate) picks the clock up instead of jumping.
        CompanyMotion fresh = new CompanyMotion(20_000L);
        fresh.advance(1_000_000);
        assertEquals(0f, fresh.progress(), 0f);
        fresh.advance(1_000_000 + WorldOverlay.FRAME_MS);
        assertTrue(fresh.progress() > 0f);
    }

    /** Once across in about 6 min, then a pause of 2 to 5 min, the pauses fixed by the day's seed. */
    @Test public void crossesInSixMinutesThenPausesTwoToFive() {
        assertEquals(6 * 60_000L, CompanyMotion.CROSS_MS);
        assertEquals(6 * 60_000L + 150_000L, CompanyMotion.cycleMs(6 * 60_000L, 150_000L));
        CompanyMotion a = new CompanyMotion(20_000L), b = new CompanyMotion(20_000L), c = new CompanyMotion(20_001L);
        long[] pa = pauses(a, 8), pb = pauses(b, 8), pc = pauses(c, 8);
        boolean differs = false;
        for (int i = 0; i < pa.length; i++) {
            assertTrue(pa[i] + "", pa[i] >= 2 * 60_000L && pa[i] <= 5 * 60_000L);
            assertEquals(pa[i], pb[i]);
            differs |= pa[i] != pc[i];
        }
        assertTrue(differs);
        assertNotEquals(CompanyMotion.daySeed(20_000L), CompanyMotion.daySeed(20_001L));
    }

    /** The company comes in from the left and leaves at the right, fully off the screen at either end. */
    @Test public void companyEntersAndLeavesOffScreen() {
        float s = 108f;   // a tenth of 1080 lines
        float[] off = CompanyMotion.OFFSETS;
        assertEquals(10, off.length);
        assertEquals(CompanyMotion.OFFSETS.length, CompanyMotion.COMPANY.length);
        // From the back to the front: the shield bearer last, the wizard ahead of all (spec § 4.2).
        assertEquals(CompanyMotion.WIZARD, CompanyMotion.COMPANY[off.length - 1]);
        assertEquals(CompanyMotion.SHIELD, CompanyMotion.COMPANY[0]);
        assertEquals(0f, off[off.length - 1], 0f);
        for (int i = 1; i < off.length; i++) assertTrue(off[i - 1] > off[i]);
        float head0 = CompanyMotion.headX(0f, W, s), head1 = CompanyMotion.headX(1f, W, s);
        assertTrue(head0 + CompanyMotion.FRONT * s <= 0f);
        assertTrue(head1 - off[0] * s - CompanyMotion.BACK * s >= W);
        // Slowly: a fraction of a pixel a frame at 1920 wide.
        float perFrame = (head1 - head0) * WorldOverlay.FRAME_MS / CompanyMotion.CROSS_MS;
        assertTrue("" + perFrame, perFrame < 0.5f);
    }

    /** Each figure steps on its own beat (the mockup's i * 1.3), from the walked time only: still when stopped. */
    @Test public void stepsComeFromTheWalkedTime() {
        int f = CompanyMotion.stepFrame(0L, 0, 16);
        assertEquals(f, CompanyMotion.stepFrame(CompanyMotion.STEP_MS, 0, 16));
        assertNotEquals(CompanyMotion.stepFrame(0L, 0, 16), CompanyMotion.stepFrame(0L, 1, 16));
        for (long t = 0; t < 3 * CompanyMotion.STEP_MS; t += 37) {
            for (int i = 0; i < 10; i++) {
                int k = CompanyMotion.stepFrame(t, i, 16);
                assertTrue(k >= 0 && k < 16);
            }
        }
    }

    /**
     * With reduced motion (or animations off) the overlay never ticks: the company stands in the middle of the
     * screen, visible and still whatever the clock says, instead of waiting unseen left of it.
     */
    @Test public void companyStandsStillWithReducedMotion() {
        assertFalse(WorldOverlay.shouldRun(World.MIDDLE_EARTH, true, true, true, false));
        CompanyMotion m = new CompanyMotion(20_000L);
        float s = 108f;
        assertFalse(m.hasWalked());
        assertTrue(m.isVisible());
        float head = m.headX(W, s);
        // The group's middle (the wizard ahead, the shield bearer last) at half the width.
        float middle = head - CompanyMotion.OFFSETS[0] * s / 2f;
        assertEquals(0.5f * W, middle, 1f);
        // Stopped again and again (onPause, a screensaver) and frames that never come: it stays where it stands.
        for (long t = 0; t <= 30 * 60_000L; t += 60_000L) {
            m.stop(t);
            assertEquals(head, m.headX(W, s), 0f);
            assertTrue(m.isVisible());
        }
        assertFalse(m.hasWalked());
        // Moving after all, it walks its way from the left as ever.
        m.start(0);
        assertTrue(m.hasWalked());
        assertEquals(CompanyMotion.headX(0f, W, s), m.headX(W, s), 0f);
    }

    /**
     * After a recreate the old overlay may stop the clock late, after the new one started it: only the overlay that
     * started it stops it, so the new one's company walks on.
     */
    @Test public void aLateStopOfAnOldOverlayKeepsTheNewOneWalking() {
        CompanyMotion m = new CompanyMotion(20_000L);
        Object oldOverlay = new Object(), newOverlay = new Object();
        m.start(0, oldOverlay);
        for (long t = 0; t <= 990; t += WorldOverlay.FRAME_MS) m.advance(t);
        m.start(1_000, newOverlay);
        float p = m.progress();
        m.stop(1_010, oldOverlay);
        assertTrue(m.isRunning());
        assertEquals(p, m.progress(), 0f);
        m.advance(1_033);
        float frame = WorldOverlay.FRAME_MS / (float) CompanyMotion.CROSS_MS;
        assertEquals(p + frame, m.progress(), 1e-6f);
        // Its own stop stops it, walked up to then.
        m.stop(1_066, newOverlay);
        assertFalse(m.isRunning());
        assertEquals(p + 2 * frame, m.progress(), 1e-6f);
    }

    /** A new name's dimmed area (or none) comes in over NAME_BLEND_MS instead of at once. */
    @Test public void dimmedAreaBlendsToTheNewName() {
        float x = 300f;
        // From a name over x to none: a quarter at the start, full at the end, in between rising.
        assertEquals(0.25f, CompanyMotion.blendAlpha(x, 136f, 520f, -1f, -1f, W, 0f), 1e-6f);
        assertEquals(1f, CompanyMotion.blendAlpha(x, 136f, 520f, -1f, -1f, W, 1f), 1e-6f);
        float half = CompanyMotion.blendAlpha(x, 136f, 520f, -1f, -1f, W, 0.5f);
        assertTrue("" + half, half > 0.3f && half < 0.95f);
        float before = 0f;
        for (float t = 0f; t <= 1f; t += 0.05f) {
            float a = CompanyMotion.blendAlpha(x, 136f, 520f, -1f, -1f, W, t);
            assertTrue(t + ": " + a, a >= before - 1e-6f);
            before = a;
        }
        // Past its end and before its start it holds.
        assertEquals(1f, CompanyMotion.blendAlpha(x, 136f, 520f, -1f, -1f, W, 3f), 1e-6f);
        assertEquals(0.25f, CompanyMotion.blendAlpha(x, 136f, 520f, -1f, -1f, W, -1f), 1e-6f);
        assertEquals(CompanyMotion.alphaAt(x, 900f, 1300f, W),
                CompanyMotion.blendAlpha(x, 136f, 520f, 900f, 1300f, W, 1f), 1e-6f);
        assertTrue(CompanyMotion.NAME_BLEND_MS >= 150 && CompanyMotion.NAME_BLEND_MS <= 300);
    }

    /** Walks one motion through {@code n} cycles a frame at a time, returning each cycle's pause. */
    private static long[] pauses(CompanyMotion m, int n) {
        long[] out = new long[n];
        long t = 0;
        m.start(t);
        for (int i = 0; i < n; i++) {
            out[i] = m.pauseMs();
            long end = t + CompanyMotion.cycleMs(CompanyMotion.CROSS_MS, out[i]);
            while (t < end) {
                t += CompanyMotion.MAX_FRAME_MS;
                m.advance(t);
            }
        }
        return out;
    }
}
