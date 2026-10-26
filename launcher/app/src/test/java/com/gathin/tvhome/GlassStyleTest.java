package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** glass_style from config.xml or a theme: rim or halo by name, glass for anything else. */
public class GlassStyleTest {

    @Test
    public void rimByNameAnyCase() {
        assertEquals(Config.GLASS_RIM, Config.glassStyle("rim"));
        assertEquals(Config.GLASS_RIM, Config.glassStyle(" Rim "));
    }

    @Test
    public void haloByNameAnyCase() {
        assertEquals(Config.GLASS_HALO, Config.glassStyle("halo"));
        assertEquals(Config.GLASS_HALO, Config.glassStyle("HALO "));
    }

    /** The glow's strength: faint white, stronger in a colour, strongest as Supernatural's warm amber light. */
    @Test
    public void warmGlowIsStrongerOnlyInAnExactAccent() {
        assertEquals(0x30, GlassView.glowAlpha(0, false, false));
        assertEquals(0x30, GlassView.glowAlpha(0, true, true));        // no accent: plain white glass
        assertEquals(0x70, GlassView.glowAlpha(0xFFE0A24A, false, false));
        assertEquals(0x70, GlassView.glowAlpha(0xFFE0A24A, true, false));
        assertEquals(0x70, GlassView.glowAlpha(0xFFE0A24A, false, true)); // reorder's lifted rim stays as it was
        assertEquals(0xC0, GlassView.glowAlpha(0xFFE0A24A, true, true));
    }

    @Test
    public void anythingElseIsGlass() {
        assertEquals(Config.GLASS_DEFAULT, Config.glassStyle("glass"));
        assertEquals(Config.GLASS_DEFAULT, Config.glassStyle(""));
        assertEquals(Config.GLASS_DEFAULT, Config.glassStyle(null));
        assertEquals(Config.GLASS_DEFAULT, Config.glassStyle("frosted"));
    }
}
