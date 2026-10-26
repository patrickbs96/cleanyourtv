package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

/**
 * Plain JVM tests for the personal days' motion: the texts come from the entries, the number in the heart is the year
 * count, long names fit, and the names, the heart and everything that moves stay out of the header and above the dock.
 * Names are placeholders only.
 */
public class PersonalMotionsTest {
    private static final LocalDate DAY = LocalDate.of(2027, 7, 1);
    private static final float HEADER_RIGHT = 76f, HEADER_BOTTOM = 34f, DOCK = 766f / 1080f * 108;

    private static SeasonsToday today(String... lines) {
        PersonalDay[] days = new PersonalDay[lines.length];
        for (int i = 0; i < lines.length; i++) days[i] = PersonalDay.decode(lines[i]);
        return SeasonsToday.resolve(DAY, List.of(days), false, false);
    }

    /** Outfit Bold is about .55 em wide per letter: a stand-in for measureText in plain JVM tests. */
    private static float estimatedWidth(String text, float size) {
        return text.length() * 0.55f * size;
    }

    @Test public void theThreePersonalDaysMove() {
        SeasonsToday birthday = today("birthday;7;1;1990;Alex");
        SeasonsToday anniversary = today("anniversary;7;1;2022;Alex & Robin");
        SeasonsToday wedding = today("wedding;7;1;2020;Alex & Robin");
        assertTrue(LookMotion.forLook(Look.BIRTHDAY, birthday) instanceof PersonalMotions.Birthday);
        assertTrue(LookMotion.forLook(Look.ANNIVERSARY, anniversary) instanceof PersonalMotions.Anniversary);
        assertTrue(LookMotion.forLook(Look.WEDDING, wedding) instanceof PersonalMotions.Wedding);
        for (SeasonsToday t : new SeasonsToday[]{birthday, anniversary, wedding}) {
            assertTrue(t.look.id, SeasonsOverlay.ticks(LookMotion.forLook(t.look, t)));
            assertNotSame(t.look.id, LookMotion.forLook(t.look, t), LookMotion.forLook(t.look, t));
        }
        assertNull(PersonalMotions.of(Look.WINTER, birthday));
        assertNull(PersonalMotions.of(Look.CHRISTMAS, birthday));
        assertNull(PersonalMotions.of(null, birthday));
        // Without a day they still build, with nothing to write.
        assertEquals("", ((PersonalMotions.Birthday) PersonalMotions.of(Look.BIRTHDAY, null)).text());
        assertEquals("0", ((PersonalMotions.Anniversary) PersonalMotions.of(Look.ANNIVERSARY, null)).number());
    }

    /** Plan Task 8: the number in the heart is the year count, String.valueOf(years), and follows the entry. */
    @Test public void heartNumberIsTheYearCount() {
        SeasonsToday t = today("anniversary;7;1;2019;Alex & Robin");
        assertEquals(Look.ANNIVERSARY, t.look);
        PersonalMotions.Anniversary heart = (PersonalMotions.Anniversary) LookMotion.forLook(t.look, t);
        assertEquals(String.valueOf(t.personal.get(0).years(DAY)), heart.number());
        assertEquals("8", heart.number());
        SeasonsToday later = today("anniversary;7;1;2015;Alex & Robin");
        assertEquals("12", ((PersonalMotions.Anniversary) LookMotion.forLook(later.look, later)).number());
    }

    /** Plan Task 8: the names drawn are the entries' (today.names()), nothing from the code. */
    @Test public void namesComeFromTheEntriesNotTheCode() {
        SeasonsToday one = today("birthday;7;1;1990;Alex");
        assertEquals(one.names(), ((PersonalMotions.Birthday) LookMotion.forLook(one.look, one)).text());
        assertEquals("Alex", ((PersonalMotions.Birthday) LookMotion.forLook(one.look, one)).text());
        SeasonsToday two = today("birthday;7;1;1990;Alex", "birthday;7;1;1992;Robin");
        assertEquals("Alex & Robin", ((PersonalMotions.Birthday) LookMotion.forLook(two.look, two)).text());
        SeasonsToday renamed = today("birthday;7;1;1990;Robin");
        assertEquals("Robin", ((PersonalMotions.Birthday) LookMotion.forLook(renamed.look, renamed)).text());
        SeasonsToday wedding = today("wedding;7;1;2020;Alex & Robin");
        assertEquals(wedding.names(), ((PersonalMotions.Wedding) LookMotion.forLook(wedding.look, wedding)).text());
        // A wedding and a birthday on one day: the birthday leads, and only its name is written.
        SeasonsToday both = today("wedding;7;1;2020;Alex & Robin", "birthday;7;1;1990;Alex");
        assertEquals(Look.BIRTHDAY, both.look);
        assertEquals("Alex", ((PersonalMotions.Birthday) LookMotion.forLook(both.look, both)).text());
    }

    /** A long name shrinks to its width; a short one keeps its size (at least 6 % of the width for a birthday). */
    @Test public void longNamesShrinkToFit() {
        float size = PersonalMotions.Birthday.NAME_SIZE, max = PersonalMotions.Birthday.NAME_MAX;
        assertTrue(size >= 0.06f * 192 - 1e-4f);
        assertEquals(size, PersonalMotions.fitSize(size, estimatedWidth("Alex", size), max), 1e-6f);
        String longest = "Alexandra-Marie & Robin-Christopher";
        float fitted = PersonalMotions.fitSize(size, estimatedWidth(longest, size), max);
        assertTrue(fitted < size);
        assertEquals(max, estimatedWidth(longest, fitted), 1e-3f);
        float names = PersonalMotions.Wedding.NAMES_SIZE, wide = PersonalMotions.Wedding.NAMES_MAX;
        float wfit = PersonalMotions.fitSize(names, estimatedWidth(longest, names), wide);
        assertTrue(estimatedWidth(longest, wfit) <= wide + 1e-3f);
        assertEquals(10f, PersonalMotions.fitSize(10f, 0f, 5f), 0f);
    }

    /**
     * The birthday's name stands under the garland and right of the cake, inside the picture, out of the header and
     * above the dock, at its widest; the confetti falls right of the header and fades above the dock.
     */
    @Test public void birthdayNameSitsUnderTheGarland() {
        float x = PersonalMotions.Birthday.NAME_X, top = PersonalMotions.Birthday.NAME_TOP;
        float half = PersonalMotions.Birthday.NAME_MAX / 2;
        float bottom = top + PersonalMotions.Birthday.NAME_SIZE * PersonalMotions.LINE;
        assertTrue(top > PersonalMotions.Birthday.garlandBottom(x - half, x + half));
        assertTrue(bottom < DOCK);
        assertTrue(x - half > HEADER_RIGHT);
        assertTrue(x - half > PersonalMotions.Birthday.CAKE_RIGHT - 3f);
        // Clear of the candles' flames and their glow.
        float candleRight = PersonalMotions.Birthday.CAKE_X + PersonalMotions.Birthday.CANDLE_DX[3]
                + PersonalMotions.Birthday.FLAME_GLOW;
        assertTrue(x - half > candleRight);
        assertTrue(x + half < 192f);
        // The garland hangs deep, its middle well below its ends.
        float x0 = PersonalMotions.Birthday.GARLAND_X0, x1 = PersonalMotions.Birthday.GARLAND_X1;
        assertTrue(PersonalMotions.Birthday.garlandY((x0 + x1) / 2) - PersonalMotions.Birthday.garlandY(x0) > 10f);
        PersonalMotions.Birthday b = new PersonalMotions.Birthday("Alex");
        assertEquals(70, b.confettiCount());
        assertTrue(b.confettiLeftmost() * 192 > HEADER_RIGHT);
        // The candles' flames and their glow above the dock, right of the header.
        assertTrue(PersonalMotions.Birthday.FLAME_Y + PersonalMotions.Birthday.FLAME_GLOW < DOCK);
        assertTrue(PersonalMotions.Birthday.CAKE_X + PersonalMotions.Birthday.CANDLE_DX[0]
                - PersonalMotions.Birthday.FLAME_GLOW > HEADER_RIGHT);
        assertTrue(SeasonMotions.FALL_END * 108 < DOCK);
    }

    /** The balloons' bow sits where the painted strings end: on the dock's top edge, only on the birthday. */
    @Test public void balloonBowSitsOnTheDockEdgeOnTheBirthdayOnly() {
        assertEquals(DOCK, BalloonBowView.KNOT_Y, 1e-4f);
        assertTrue(BalloonBowView.KNOT_X > 0f && BalloonBowView.KNOT_X < PersonalMotions.Birthday.CAKE_X - 15.36f);
        assertTrue(BalloonBowView.shows(Look.BIRTHDAY));
        for (Look look : Look.values()) {
            if (look != Look.BIRTHDAY) assertFalse(look.id, BalloonBowView.shows(look));
        }
        assertFalse(BalloonBowView.shows(null));
    }

    /** The number stands in the middle of the heart: its box is inside the outline; the beam ends at the heart's tip. */
    @Test public void numberSitsInTheHeartAndTheBeamEndsAtItsTip() {
        assertEquals(17f, PersonalMotions.Anniversary.heartTip(), 1e-4f);
        float top = PersonalMotions.Anniversary.heartTop();
        assertTrue(top < -11f && top > -13f);
        float mid = PersonalMotions.Anniversary.numberY();
        assertEquals((top + 17f) / 2, mid, 1e-4f);
        // A two-digit count at its size: every corner of its box inside the heart (heart units).
        float size = PersonalMotions.Anniversary.NUMBER_SIZE, w = estimatedWidth("12", size) / 2, h = size * 0.72f / 2;
        for (float dx : new float[]{-w, w}) {
            for (float dy : new float[]{-h, h}) {
                assertTrue(dx + "," + dy, PersonalMotions.Anniversary.inHeart(dx, mid + dy));
            }
        }
        assertTrue(PersonalMotions.Anniversary.inHeart(0f, mid));
        assertFalse(PersonalMotions.Anniversary.inHeart(0f, -8f));    // the dip between the lobes
        assertFalse(PersonalMotions.Anniversary.inHeart(15f, 12f));
        // A three-digit count shrinks into NUMBER_MAX, which fits the heart at the number's height.
        float max = PersonalMotions.Anniversary.NUMBER_MAX;
        assertTrue(PersonalMotions.Anniversary.inHeart(-max / 2, mid) && PersonalMotions.Anniversary.inHeart(max / 2, mid));
        for (double t = 0; t < 30; t += 0.7) {
            float bob = PersonalMotions.Anniversary.bob(t);
            assertEquals(PersonalMotions.Anniversary.HEART_Y + bob + 17f * PersonalMotions.Anniversary.HEART_K,
                    PersonalMotions.Anniversary.beamTop(bob), 1e-4f);
            // The beam rises from the lens to the tip.
            assertTrue(PersonalMotions.Anniversary.beamTop(bob) < PersonalMotions.Anniversary.LENS_Y);
        }
    }

    /** The holo heart flickers calmly (no strobe); the stars twinkle, none in the header; all above the dock. */
    @Test public void anniversaryFlickersCalmlyAndStaysInItsPlace() {
        float last = PersonalMotions.Anniversary.flicker(0), lo = 1f, hi = 0f;
        for (double t = 1.0 / 30; t < 120; t += 1.0 / 30) {
            float f = PersonalMotions.Anniversary.flicker(t);
            assertTrue(f >= 0.6f && f <= 1f);
            assertTrue("jump at " + t, Math.abs(f - last) <= 0.08f);
            lo = Math.min(lo, f);
            hi = Math.max(hi, f);
            last = f;
        }
        assertTrue(hi - lo > 0.15f);
        PersonalMotions.Anniversary a = new PersonalMotions.Anniversary(4);
        assertTrue(a.starsShown() > 30 && a.starsShown() < 40);
        float k = PersonalMotions.Anniversary.HEART_K, x = PersonalMotions.Anniversary.HEART_X;
        assertTrue(x - 16 * k > HEADER_RIGHT);
        assertTrue(PersonalMotions.Anniversary.HEART_Y - PersonalMotions.Anniversary.BOB
                + PersonalMotions.Anniversary.heartTop() * k > HEADER_BOTTOM);
        assertTrue(PersonalMotions.Anniversary.LENS_Y < DOCK);
        assertTrue(PersonalMotions.Anniversary.STARS_TOP * 108 < DOCK);
        assertEquals(PersonalMotions.Anniversary.LENS_X, x, 0f);
    }

    /**
     * Wedding: the lights hang on the arch where personal.py paints them and their glow stays above the dock; the
     * sparkle runs over the rings; the petals float right of the header; the names stand over the arch.
     */
    @Test public void weddingStaysOnTheArchAndOutOfTheHeader() {
        PersonalMotions.Wedding w = new PersonalMotions.Wedding("Alex & Robin");
        float[][] lights = w.lights();
        assertEquals(22, lights.length);
        float ax = PersonalMotions.Wedding.ARCH_X, aw = PersonalMotions.Wedding.ARCH_W;
        for (float[] l : lights) {
            assertTrue(l[0] >= ax - aw - 1e-3f && l[0] <= ax + aw + 1e-3f);
            assertTrue(l[1] >= PersonalMotions.Wedding.ARCH_FOOT - PersonalMotions.Wedding.ARCH_H - 1e-3f);
            assertTrue(l[1] + PersonalMotions.Wedding.LIGHT_GLOW < DOCK);
        }
        assertTrue(PersonalMotions.Wedding.lowestLight() + PersonalMotions.Wedding.LIGHT_GLOW < DOCK);
        assertTrue(ax - aw - PersonalMotions.Wedding.LIGHT_GLOW > HEADER_RIGHT);
        // The path: feet on the ground, the top in the middle.
        assertEquals(PersonalMotions.Wedding.ARCH_FOOT, PersonalMotions.Wedding.archPoint(0f)[1], 1e-4f);
        assertEquals(ax, PersonalMotions.Wedding.archPoint(0.5f)[0], 1e-3f);
        assertEquals(PersonalMotions.Wedding.ARCH_FOOT - PersonalMotions.Wedding.ARCH_H,
                PersonalMotions.Wedding.archPoint(0.5f)[1], 1e-3f);
        // The sparkle stays on the rings' upper arcs and fades in and out.
        float[] s = new float[3];
        float r = PersonalMotions.Wedding.RING_R, dx = PersonalMotions.Wedding.RING_DX * r;
        for (double t = 0; t < 20; t += 0.05) {
            PersonalMotions.Wedding.sparkle(t, s);
            assertTrue(s[1] <= PersonalMotions.Wedding.RING_Y + 1e-3f);
            float dl = (float) Math.hypot((s[0] - (ax - dx)) / r, (s[1] - PersonalMotions.Wedding.RING_Y) / (0.9f * r));
            float dr = (float) Math.hypot((s[0] - (ax + dx)) / r, (s[1] - PersonalMotions.Wedding.RING_Y) / (0.9f * r));
            assertTrue(Math.abs(dl - 1f) < 1e-3f || Math.abs(dr - 1f) < 1e-3f);
            assertTrue(s[2] >= 0f && s[2] <= 1f);
        }
        assertEquals(16, w.petalCount());
        assertTrue((w.petalBox()[0] - 0.02f) * 192 > HEADER_RIGHT);
        // The names over the arch: out of the header, inside the picture, above the arch's top.
        float half = PersonalMotions.Wedding.NAMES_MAX / 2, x = PersonalMotions.Wedding.NAMES_X;
        assertTrue(x - half > HEADER_RIGHT && x + half < 192f);
        float bottom = PersonalMotions.Wedding.NAMES_TOP + PersonalMotions.Wedding.NAMES_SIZE * PersonalMotions.LINE;
        assertTrue(bottom < PersonalMotions.Wedding.ARCH_FOOT - PersonalMotions.Wedding.ARCH_H - 2f);
        assertTrue(PersonalMotions.Wedding.NAMES_TOP > 0f);
    }
}
