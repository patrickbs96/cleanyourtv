package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Plain JVM tests for one personal day's saved line: what survives a save and what is thrown away. */
public class PersonalDayTest {
    @Test
    public void roundTripKeepsSeparatorsInNames() {
        PersonalDay d = PersonalDay.decode("anniversary;3;14;2019;A%3BB & C");
        assertEquals("A;B & C", d.names);
        assertEquals(d.encode(), PersonalDay.decode(d.encode()).encode());
    }

    @Test
    public void percentAndLineBreakSurviveASave() {
        PersonalDay d = new PersonalDay(PersonalDay.Kind.BIRTHDAY, 2, 29, 2000, "Alex 100%\nRobin");
        PersonalDay back = PersonalDay.decode(d.encode());
        assertEquals("Alex 100%\nRobin", back.names);
        assertEquals(PersonalDay.Kind.BIRTHDAY, back.kind);
        assertEquals(2, back.month);
        assertEquals(29, back.day);
        assertEquals(2000, back.startYear);
    }

    @Test
    public void brokenLinesDecodeToNull() {
        assertNull(PersonalDay.decode("nonsense"));
        assertNull(PersonalDay.decode("birthday;13;1;1990;Alex"));
        assertNull(PersonalDay.decode("birthday;4;31;1990;Alex"));
        assertNull(PersonalDay.decode("party;1;1;1990;Alex"));
        assertNull(PersonalDay.decode("birthday;1;1;1990;  "));
        assertNull(PersonalDay.decode(""));
    }
}
