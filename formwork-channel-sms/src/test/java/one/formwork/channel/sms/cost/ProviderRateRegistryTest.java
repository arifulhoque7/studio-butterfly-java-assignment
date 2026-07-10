package one.formwork.channel.sms.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRateRegistryTest {

    private ProviderRateRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProviderRateRegistry();
    }

    @Nested
    class GetRate {

        @Test
        void getRate_KnownProviderAndCountry_ReturnsRate() {
            BigDecimal rate = registry.getRate("TWILIO", "DE");
            assertEquals(new BigDecimal("0.0750"), rate);
        }

        @Test
        void getRate_UnknownCountry_FallsBackToDefault() {
            BigDecimal rate = registry.getRate("TWILIO", "JP");
            assertNotNull(rate);
            // Falls back to DEFAULT_RATE (0.07) since no JP rate set
            assertEquals(new BigDecimal("0.07"), rate);
        }

        @Test
        void getRate_UnknownProvider_ReturnsDefaultRate() {
            BigDecimal rate = registry.getRate("UNKNOWN_PROVIDER", "DE");
            assertEquals(new BigDecimal("0.07"), rate);
        }

        @Test
        void getRate_CustomRate_ReturnsOverride() {
            registry.setRate("CUSTOM", "JP", new BigDecimal("0.12"));
            BigDecimal rate = registry.getRate("CUSTOM", "JP");
            assertEquals(new BigDecimal("0.12"), rate);
        }
    }

    @Nested
    class SetRate {

        @Test
        void setRate_NewEntry_Persists() {
            registry.setRate("NEW_PROVIDER", "NZ", new BigDecimal("0.05"));
            assertEquals(new BigDecimal("0.05"), registry.getRate("NEW_PROVIDER", "NZ"));
        }

        @Test
        void setRate_Override_ReplacesOld() {
            registry.setRate("TWILIO", "DE", new BigDecimal("0.10"));
            assertEquals(new BigDecimal("0.10"), registry.getRate("TWILIO", "DE"));
        }
    }

    @Test
    void getAllRates_ContainsDefaults() {
        var rates = registry.getAllRates();
        assertFalse(rates.isEmpty());
        assertTrue(rates.containsKey("TWILIO::DE"));
        assertTrue(rates.containsKey("AWS_SNS::US"));
    }

    @Test
    void getRate_CaseInsensitive_MatchesUpperCase() {
        registry.setRate("twilio", "de", new BigDecimal("0.08"));
        assertEquals(new BigDecimal("0.08"), registry.getRate("TWILIO", "DE"));
    }
}

