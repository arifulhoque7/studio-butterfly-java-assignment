package one.formwork.channel.sms.cost;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SK-11: Registry for per-provider, per-country SMS rates.
 * Defaults to standard EUR rates; can be overridden at runtime.
 */
@Component
public class ProviderRateRegistry {

    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.07");

    /** Key format: "PROVIDER::COUNTRY_CODE" */
    private final Map<String, BigDecimal> rates = new ConcurrentHashMap<>();

    public ProviderRateRegistry() {
        // Default rates per provider for Germany
        setRate("TWILIO", "DE", new BigDecimal("0.0750"));
        setRate("VONAGE", "DE", new BigDecimal("0.0650"));
        setRate("AWS_SNS", "DE", new BigDecimal("0.0460"));
        setRate("BUDGET_SMS", "DE", new BigDecimal("0.0550"));
        setRate("MESSAGEBIRD", "DE", new BigDecimal("0.0680"));
        // US rates
        setRate("TWILIO", "US", new BigDecimal("0.0079"));
        setRate("VONAGE", "US", new BigDecimal("0.0068"));
        setRate("AWS_SNS", "US", new BigDecimal("0.0065"));
    }

    public BigDecimal getRate(String provider, String countryCode) {
        BigDecimal rate = rates.get(key(provider, countryCode));
        if (rate != null) return rate;
        // Fallback: try provider default
        rate = rates.get(key(provider, "DEFAULT"));
        return rate != null ? rate : DEFAULT_RATE;
    }

    public void setRate(String provider, String countryCode, BigDecimal rate) {
        rates.put(key(provider, countryCode), rate);
    }

    public Map<String, BigDecimal> getAllRates() {
        return Map.copyOf(rates);
    }

    private static String key(String provider, String countryCode) {
        return provider.toUpperCase() + "::" + countryCode.toUpperCase();
    }
}

