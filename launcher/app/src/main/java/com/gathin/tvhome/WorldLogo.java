package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * A world's own logo picture, which the household puts only on its TV (Supernatural spec § 4.4, § 4.5):
 * {@code world_logos/world_logo_<id>.png} in {@link Config#dir}, beside the theme files and not part of them, so
 * a theme push and {@code tools/theme.sh --reset} leave it alone. {@code tools/theme.sh --logo <id> <file.png>}
 * puts it there and {@code --logo <id> --remove} takes it away; whether the file is there is the on/off switch.
 * A world that finds no usable picture draws its own lettering instead. The picture never comes from the
 * repository.
 */
final class WorldLogo {
    static final String DIR = "world_logos";
    /** The largest file read, the same limit tools/theme.sh refuses above. */
    static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private WorldLogo() { }

    static String fileName(World world) {
        return "world_logo_" + world.id + ".png";
    }

    /** The path under {@link Config#dir}, e.g. "world_logos/world_logo_supernatural.png". */
    static String path(World world) {
        return DIR + "/" + fileName(world);
    }

    /** Where the world's logo lives, whether or not it is there; null without external storage. */
    static File file(Context ctx, World world) {
        File dir = Config.dir(ctx);
        return dir == null ? null : new File(dir, path(world));
    }

    /** True for a non-empty file of at most {@link #MAX_BYTES} that starts with the PNG signature. */
    static boolean acceptable(long size, byte[] header) {
        if (size <= 0 || size > MAX_BYTES || header == null || header.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (header[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    /**
     * The smallest power-of-two sample size that brings a width x height picture within maxW x maxH, counting a
     * partly covered pixel as a whole one; 0 when either has no area.
     */
    static int sampleSize(int width, int height, int maxW, int maxH) {
        if (width <= 0 || height <= 0 || maxW <= 0 || maxH <= 0) return 0;
        int sample = 1;
        while (((width + sample - 1) / sample > maxW || (height + sample - 1) / sample > maxH) && sample < (1 << 30)) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * The world's logo decoded to at most maxW x maxH, or null when there is none, it is not a PNG of at most
     * 2 MB, or it cannot be decoded. Never throws: a broken picture only means the lettering stays.
     */
    static Bitmap load(Context ctx, World world, int maxW, int maxH) {
        try {
            File f = file(ctx, world);
            if (f == null || !f.isFile()) return null;
            byte[] header = new byte[PNG_SIGNATURE.length];
            int read;
            try (InputStream in = new FileInputStream(f)) {
                read = in.read(header);
            }
            if (read != header.length || !acceptable(f.length(), header)) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), options);
            int sample = sampleSize(options.outWidth, options.outHeight, maxW, maxH);
            if (sample == 0) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            Bitmap decoded = BitmapFactory.decodeFile(f.getPath(), options);
            if (decoded == null) return null;
            if (decoded.getWidth() > maxW || decoded.getHeight() > maxH) {
                // A decoder that rounded the other way: scale the last pixels off rather than draw past the box.
                float k = Math.min((float) maxW / decoded.getWidth(), (float) maxH / decoded.getHeight());
                Bitmap fitted = Bitmap.createScaledBitmap(decoded, Math.max(1, (int) (decoded.getWidth() * k)),
                        Math.max(1, (int) (decoded.getHeight() * k)), true);
                if (fitted != decoded) decoded.recycle();
                return fitted;
            }
            return decoded;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }
}
