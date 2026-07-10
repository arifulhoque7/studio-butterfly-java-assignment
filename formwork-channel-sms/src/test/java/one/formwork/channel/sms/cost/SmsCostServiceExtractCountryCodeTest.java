package one.formwork.channel.sms.cost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SmsCostServiceExtractCountryCodeTest {

    @ParameterizedTest
    @CsvSource({
        "0049171123456, DE",
        "004317123456, AT",
        "004179123456, CH",
        "004420123456, GB",
        "+1234567890, US",
        "+33612345678, FR",
        "+39312345678, IT",
        "+34612345678, ES",
        "+31612345678, NL",
        "+81312345678, 813",
        ", XX",
        "ab, XX",
        "12345, XX"
    })
    void extractCountryCode_VariousFormats(String phone, String expected) {
        assertEquals(expected, SmsCostService.extractCountryCode(phone));
    }

    @Test
    void extractCountryCode_NullInput() {
        assertEquals("XX", SmsCostService.extractCountryCode(null));
    }

    @Test
    void extractCountryCode_WithSpacesAndDashes() {
        assertEquals("DE", SmsCostService.extractCountryCode("+49 171 1234-567"));
    }

    @Test
    void maskRecipient_Null() {
        assertEquals("***", SmsCostService.maskRecipient(null));
    }

    @Test
    void maskRecipient_Short() {
        assertEquals("***", SmsCostService.maskRecipient("123"));
    }

    @Test
    void maskRecipient_Normal() {
        String result = SmsCostService.maskRecipient("+491711234567");
        assertTrue(result.startsWith("+491"));
        assertTrue(result.endsWith("67"));
        assertTrue(result.contains("***"));
    }
}
