package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import android.view.KeyEvent;

import org.junit.Test;

/** Plain JVM tests for which keys end the anniversary crawl and which of them are eaten. */
public class CrawlKeysTest {
    private static final int DOWN = KeyEvent.ACTION_DOWN;
    private static final int UP = KeyEvent.ACTION_UP;
    private static final int BACK = KeyEvent.KEYCODE_BACK;
    private static final int OK = KeyEvent.KEYCODE_DPAD_CENTER;
    private static final int LEFT = KeyEvent.KEYCODE_DPAD_LEFT;
    private static final long START = 10_000;

    private static CrawlKeys started() {
        CrawlKeys k = new CrawlKeys();
        k.started(START);
        return k;
    }

    @Test
    public void withoutACrawlEveryKeyPasses() {
        CrawlKeys k = new CrawlKeys();
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, BACK, DOWN, 0, 500));
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, BACK, UP, 0, 500));
    }

    @Test
    public void aStaleReleaseIsEatenButTheCrawlRollsOn() {
        // Back pressed in an app that closed on its DOWN; the UP arrives once Home's crawl has started.
        CrawlKeys k = started();
        assertEquals(CrawlKeys.Verdict.EAT, k.on(true, BACK, UP, 0, START - 80));
        // The next fresh press still ends it.
        assertEquals(CrawlKeys.Verdict.END, k.on(true, BACK, DOWN, 0, START + 3000));
    }

    @Test
    public void aStaleRepeatIsEatenButTheCrawlRollsOn() {
        CrawlKeys k = started();
        assertEquals(CrawlKeys.Verdict.EAT, k.on(true, LEFT, DOWN, 1, START - 400));
        assertEquals(CrawlKeys.Verdict.EAT, k.on(true, LEFT, DOWN, 2, START - 400));
        assertEquals(CrawlKeys.Verdict.EAT, k.on(true, LEFT, UP, 0, START - 400));
    }

    @Test
    public void aRepeatWithoutItsDownIsStaleEvenWithALaterDownTime() {
        CrawlKeys k = started();
        assertEquals(CrawlKeys.Verdict.EAT, k.on(true, LEFT, DOWN, 3, START + 10));
        assertEquals(CrawlKeys.Verdict.EAT, k.on(true, LEFT, UP, 0, START + 10));
    }

    @Test
    public void aFreshPressEndsItAndItsRepeatsAndReleaseAreEaten() {
        CrawlKeys k = started();
        assertEquals(CrawlKeys.Verdict.END, k.on(true, OK, DOWN, 0, START + 2000));
        assertEquals(CrawlKeys.Verdict.EAT, k.on(false, OK, DOWN, 1, START + 2000));
        assertEquals(CrawlKeys.Verdict.EAT, k.on(false, OK, UP, 0, START + 2000));
        // After its release, OK works again.
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, OK, DOWN, 0, START + 5000));
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, OK, UP, 0, START + 5000));
    }

    @Test
    public void aPressAtTheVeryStartCounts() {
        assertEquals(CrawlKeys.Verdict.END, started().on(true, BACK, DOWN, 0, START));
    }

    @Test
    public void anotherKeyWhileTheEndingKeyIsHeldDoesNotReleaseIt() {
        CrawlKeys k = started();
        assertEquals(CrawlKeys.Verdict.END, k.on(true, BACK, DOWN, 0, START + 1000));
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, LEFT, DOWN, 0, START + 1200));
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, LEFT, UP, 0, START + 1200));
        assertEquals(CrawlKeys.Verdict.EAT, k.on(false, BACK, UP, 0, START + 1000));
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, BACK, DOWN, 0, START + 4000));
    }

    @Test
    public void aLostReleaseDoesNotEatTheNextPressOfThatKey() {
        CrawlKeys k = started();
        assertEquals(CrawlKeys.Verdict.END, k.on(true, BACK, DOWN, 0, START + 1000));
        // Its UP never came (focus moved); a new press of Back is a new press.
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, BACK, DOWN, 0, START + 9000));
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, BACK, UP, 0, START + 9000));
    }

    @Test
    public void resetForgetsTheEndingKey() {
        CrawlKeys k = started();
        assertEquals(CrawlKeys.Verdict.END, k.on(true, BACK, DOWN, 0, START + 1000));
        k.reset();
        assertEquals(CrawlKeys.Verdict.PASS, k.on(false, BACK, UP, 0, START + 1000));
    }
}
