package one.formwork.channel.sms.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Finding 5: multi-segment counting so long messages are not under-billed as 1 segment. */
class SegmentCalculatorTest {

    @Test
    void gsm7_upTo160_isOneSegment() {
        assertEquals(1, SegmentCalculator.segments("a".repeat(160)));
    }

    @Test
    void gsm7_161_isTwoSegments() {
        assertEquals(2, SegmentCalculator.segments("a".repeat(161)));
    }

    @Test
    void gsm7_306_isTwoSegments() {
        assertEquals(2, SegmentCalculator.segments("a".repeat(306)));
    }

    @Test
    void gsm7_307_isThreeSegments() {
        assertEquals(3, SegmentCalculator.segments("a".repeat(307)));
    }

    @Test
    void ucs2_upTo70_isOneSegment() {
        assertEquals(1, SegmentCalculator.segments("中".repeat(70)));
    }

    @Test
    void ucs2_71_isTwoSegments() {
        assertEquals(2, SegmentCalculator.segments("中".repeat(71)));
    }

    @Test
    void gsm7ExtensionChar_countsAsTwoSeptets() {
        assertEquals(1, SegmentCalculator.segments("€".repeat(80)));  // 160 septets
        assertEquals(2, SegmentCalculator.segments("€".repeat(81)));  // 162 septets
    }

    @Test
    void emptyOrNull_isOneSegment() {
        assertEquals(1, SegmentCalculator.segments(""));
        assertEquals(1, SegmentCalculator.segments(null));
    }
}
