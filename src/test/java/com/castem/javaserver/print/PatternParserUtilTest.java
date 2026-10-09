package com.castem.javaserver.print;

import com.castem.javaserver.print.util.PatternParserUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatternParserUtilTest {

    @Test
    void testParseCoatingPatternStandard() {
        PatternParserUtil.CoatingResult res = PatternParserUtil.parseCoatingPattern("E5.5D3A");
        assertNotNull(res);
        assertEquals("E", res.letter());
        assertEquals("5.5", res.thickness());
        assertEquals("D", res.dLetter());
        assertEquals(3, res.dNumber());
        assertEquals("A", res.lastLetter());
    }

    @Test
    void testParseCoatingPatternWithoutSuffix() {
        PatternParserUtil.CoatingResult res = PatternParserUtil.parseCoatingPattern("W10.0D4");
        assertNotNull(res);
        assertEquals("W", res.letter());
        assertEquals("10.0", res.thickness());
        assertEquals("D", res.dLetter());
        assertEquals(4, res.dNumber());
        assertNull(res.lastLetter());
    }

    @Test
    void testParseCoatingPatternInvalid() {
        assertNull(PatternParserUtil.parseCoatingPattern("---"));
        assertNull(PatternParserUtil.parseCoatingPattern("INVALID"));
        assertNull(PatternParserUtil.parseCoatingPattern(null));
    }

    @Test
    void testParseSlipSizeFraction() {
        PatternParserUtil.SlipSizeResult res = PatternParserUtil.parseSlipSize("1/2");
        assertNotNull(res);
        assertEquals("1", res.first());
        assertEquals("2", res.second());
    }

    @Test
    void testParseSlipSizeSingle() {
        PatternParserUtil.SlipSizeResult res = PatternParserUtil.parseSlipSize("1");
        assertNotNull(res);
        assertEquals("1", res.first());
        assertEquals("", res.second());
    }
}
