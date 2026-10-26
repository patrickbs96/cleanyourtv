package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldLogoTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Test public void badOrHugeFileFallsBack() {
        assertTrue(WorldLogo.acceptable(1024, PNG));
        assertTrue(WorldLogo.acceptable(WorldLogo.MAX_BYTES, PNG));
        assertFalse("over 2 MB", WorldLogo.acceptable(WorldLogo.MAX_BYTES + 1, PNG));
        assertFalse("empty", WorldLogo.acceptable(0, PNG));
        assertFalse("a JPEG is no PNG", WorldLogo.acceptable(1024,
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F'}));
        assertFalse("text", WorldLogo.acceptable(1024, "not a png".getBytes()));
        assertFalse("header cut short", WorldLogo.acceptable(1024, new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertFalse(WorldLogo.acceptable(1024, null));
        assertEquals(2 * 1024 * 1024, WorldLogo.MAX_BYTES);
    }

    @Test public void eachWorldHasItsOwnName() {
        assertEquals("world_logos/world_logo_supernatural.png", WorldLogo.path(World.SUPERNATURAL));
        assertEquals("world_logos/world_logo_hogwarts.png", WorldLogo.path(World.HOGWARTS));
        assertEquals("world_logo_clean.png", WorldLogo.fileName(World.CLEAN));
    }

    @Test public void decodesSmallerThanTheBox() {
        assertEquals(1, WorldLogo.sampleSize(400, 100, 400, 100));
        assertEquals(1, WorldLogo.sampleSize(200, 50, 400, 100));
        assertEquals(2, WorldLogo.sampleSize(401, 100, 400, 100));
        assertEquals(4, WorldLogo.sampleSize(4000, 1000, 1000, 400));
        assertEquals("the height limits", 8, WorldLogo.sampleSize(800, 4000, 1000, 500));
        // An odd size rounds up, so the decoded picture is never a pixel over the box.
        assertEquals(4, WorldLogo.sampleSize(801, 10, 400, 10));
    }

    @Test public void anEmptyBoxOrPictureDecodesNothing() {
        assertEquals(0, WorldLogo.sampleSize(400, 100, 0, 100));
        assertEquals(0, WorldLogo.sampleSize(0, 100, 400, 100));
        assertEquals(0, WorldLogo.sampleSize(400, -1, 400, 100));
    }
}
