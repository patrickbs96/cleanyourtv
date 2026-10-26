package com.gathin.tvhome;

import android.view.KeyEvent;

/**
 * Which keys end Seasons' anniversary crawl (MainActivity.dispatchKeyEvent). Only a fresh press made while it rolls
 * ends it: a DOWN with no repeat, pressed at or after the crawl's start. What is left of a press from before (the
 * release of the Back that closed an app on its DOWN, the repeats of a key held across the return) is eaten but
 * leaves the crawl running, so the once-a-day crawl is not lost within a frame of starting. The press that ended
 * it is eaten whole: its repeats and its release, also when another key is pressed in between. A new DOWN of that
 * key (its release got lost) is a new press and passes.
 */
final class CrawlKeys {

    enum Verdict {
        /** Not the crawl's: the key goes on as usual. */
        PASS,
        /** Eaten, nothing else happens. */
        EAT,
        /** Eaten, and the crawl ends. */
        END
    }

    private static final int NO_KEY = -1;

    /** SystemClock.uptimeMillis() when the crawl started, the clock KeyEvent.getDownTime() uses. */
    private long start = Long.MAX_VALUE;
    /** The key whose press ended the crawl, until its release; NO_KEY when none. */
    private int endKey = NO_KEY;

    /** The crawl starts rolling now. */
    void started(long uptime) {
        start = uptime;
        endKey = NO_KEY;
    }

    /** Home left: nothing more of any press is eaten. */
    void reset() {
        endKey = NO_KEY;
    }

    /**
     * What to do with this key event; {@code playing} is whether the crawl still rolls. The arguments are the
     * event's key code, action, repeat count and down time.
     */
    Verdict on(boolean playing, int code, int action, int repeat, long downTime) {
        if (playing) {
            if (action == KeyEvent.ACTION_DOWN && repeat == 0 && downTime >= start) {
                endKey = code;
                return Verdict.END;
            }
            return Verdict.EAT;
        }
        if (endKey == NO_KEY || code != endKey) return Verdict.PASS;
        if (action == KeyEvent.ACTION_DOWN && repeat == 0) {
            endKey = NO_KEY;
            return Verdict.PASS;
        }
        if (action == KeyEvent.ACTION_UP) endKey = NO_KEY;
        return Verdict.EAT;
    }
}
