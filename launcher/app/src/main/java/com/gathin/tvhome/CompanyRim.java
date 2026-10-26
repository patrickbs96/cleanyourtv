package com.gathin.tvhome;

/**
 * The company's night back light (spec § 4.2, Patrick's TV check 27.10.) as plain pixel arithmetic on a figure's
 * finished alpha mask, all its parts together: a thin edge (the mask dilated, minus the mask) and a faint glow (the
 * mask blurred, leaning towards the crater on the upper left), both only outside the figure. So only the outer contour
 * lights up, never a seam between head and body, horse and rider, and the figure stays dark inside. CompanySilhouettes
 * runs it once per size on its sharp masks; nothing of it per frame.
 */
final class CompanyRim {
    private CompanyRim() {}

    /**
     * The rim round {@code mask} ({@code w} x {@code h} alpha bytes, row after row): the mask dilated by {@code edge}
     * pixels at full strength, or blurred over about {@code glow} pixels (two box passes) at {@code glowAlpha}, the
     * greater of the two, times what the mask leaves uncovered. The glow comes from the mask moved {@code shiftX},
     * {@code shiftY} pixels left and up, so it reaches further out on that side.
     */
    static byte[] outline(byte[] mask, int w, int h, int edge, int glow, float glowAlpha, int shiftX, int shiftY) {
        int n = w * h;
        int[] m = new int[n];
        for (int i = 0; i < n; i++) m[i] = mask[i] & 0xFF;
        int[] dilated = maxFilter(m, w, h, edge);
        int[] shifted = new int[n];
        for (int y = 0; y < h; y++) {
            int sy = y + shiftY;
            if (sy < 0 || sy >= h) continue;
            for (int x = 0; x < w; x++) {
                int sx = x + shiftX;
                if (sx >= 0 && sx < w) shifted[y * w + x] = m[sy * w + sx];
            }
        }
        int[] blurred = boxBlur(boxBlur(shifted, w, h, glow), w, h, glow);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            float v = Math.max(dilated[i], glowAlpha * blurred[i]);
            out[i] = (byte) Math.round(v * (255 - m[i]) / 255f);
        }
        return out;
    }

    /** The greatest value within {@code r} pixels across and down (a square), outside counting as 0. */
    private static int[] maxFilter(int[] a, int w, int h, int r) {
        int[] across = new int[a.length], out = new int[a.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int best = 0;
                for (int k = Math.max(0, x - r); k <= Math.min(w - 1, x + r); k++) best = Math.max(best, a[y * w + k]);
                across[y * w + x] = best;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int best = 0;
                for (int k = Math.max(0, y - r); k <= Math.min(h - 1, y + r); k++) best = Math.max(best, across[k * w + x]);
                out[y * w + x] = best;
            }
        }
        return out;
    }

    /** The mean over 2r + 1 pixels across, then down (a running sum), outside counting as 0. */
    private static int[] boxBlur(int[] a, int w, int h, int r) {
        int[] across = new int[a.length], out = new int[a.length];
        int size = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int sum = 0;
            for (int k = 0; k <= Math.min(w - 1, r); k++) sum += a[y * w + k];
            for (int x = 0; x < w; x++) {
                across[y * w + x] = sum / size;
                if (x + r + 1 < w) sum += a[y * w + x + r + 1];
                if (x - r >= 0) sum -= a[y * w + x - r];
            }
        }
        for (int x = 0; x < w; x++) {
            int sum = 0;
            for (int k = 0; k <= Math.min(h - 1, r); k++) sum += across[k * w + x];
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum / size;
                if (y + r + 1 < h) sum += across[(y + r + 1) * w + x];
                if (y - r >= 0) sum -= across[(y - r) * w + x];
            }
        }
        return out;
    }
}
