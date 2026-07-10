package one.formwork.channel.sms.cost;

import one.formwork.channel.sms.api.SmsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SK-11: Service for tracking and querying SMS costs.
 */
@Service
public class SmsCostService {

    private static final Logger log = LoggerFactory.getLogger(SmsCostService.class);

    private final SmsCostRepository repository;
    private final ProviderRateRegistry rateRegistry;

    public SmsCostService(SmsCostRepository repository, ProviderRateRegistry rateRegistry) {
        this.repository = repository;
        this.rateRegistry = rateRegistry;
    }

    /**
     * Record the cost for an SMS that was just sent.
     */
    @Transactional
    public SmsCostEntity recordCost(UUID tenantId, String recipient, SmsResult result) {
        if (!result.isSuccess()) {
            log.debug("Skipping cost recording for failed SMS: {}", result.errorCode());
            return null;
        }

        String countryCode = extractCountryCode(recipient);
        BigDecimal costPerSegment = rateRegistry.getRate(result.provider(), countryCode);
        int segments = Math.max(result.segmentCount(), 1);
        BigDecimal totalCost = costPerSegment.multiply(BigDecimal.valueOf(segments));

        SmsCostEntity entity = new SmsCostEntity();
        entity.setTenantId(tenantId);
        entity.setMessageId(result.messageId());
        entity.setProvider(result.provider());
        entity.setRecipient(maskRecipient(recipient));
        entity.setSegmentCount(segments);
        entity.setCostPerSegment(costPerSegment);
        entity.setTotalCost(totalCost);
        entity.setCurrency("EUR");
        entity.setCountryCode(countryCode);

        SmsCostEntity saved = repository.save(entity);
        log.info("SMS cost recorded: tenant={}, provider={}, cost={} EUR, segments={}",
                tenantId, result.provider(), totalCost, segments);
        return saved;
    }

    /**
     * Get total cost for a tenant in a month.
     */
    public BigDecimal getMonthlyCost(UUID tenantId, YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal total = repository.sumCostByTenantAndPeriod(tenantId, from, to);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get cost breakdown by provider for a period.
     */
    public Map<String, ProviderCostSummary> getCostBreakdown(UUID tenantId, Instant from, Instant to) {
        List<Object[]> rows = repository.costBreakdownByProvider(tenantId, from, to);
        Map<String, ProviderCostSummary> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String provider = (String) row[0];
            BigDecimal totalCost = (BigDecimal) row[1];
            long count = (Long) row[2];
            result.put(provider, new ProviderCostSummary(provider, totalCost, count));
        }
        return result;
    }

    /**
     * Get SMS count for a tenant in a period.
     */
    public long getSmsCount(UUID tenantId, Instant from, Instant to) {
        return repository.countByTenantIdAndSentAtBetween(tenantId, from, to);
    }

    static String extractCountryCode(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 3) return "XX";
        String cleaned = phoneNumber.replaceAll("[^+0-9]", "");
        if (cleaned.startsWith("+49") || cleaned.startsWith("0049")) return "DE";
        if (cleaned.startsWith("+43") || cleaned.startsWith("0043")) return "AT";
        if (cleaned.startsWith("+41") || cleaned.startsWith("0041")) return "CH";
        if (cleaned.startsWith("+44") || cleaned.startsWith("0044")) return "GB";
        if (cleaned.startsWith("+1")) return "US";
        if (cleaned.startsWith("+33")) return "FR";
        if (cleaned.startsWith("+39")) return "IT";
        if (cleaned.startsWith("+34")) return "ES";
        if (cleaned.startsWith("+31")) return "NL";
        if (cleaned.startsWith("+")) return cleaned.substring(1, Math.min(4, cleaned.length()));
        return "XX";
    }

    static String maskRecipient(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) return "***";
        return phoneNumber.substring(0, 4) + "***" + phoneNumber.substring(phoneNumber.length() - 2);
    }

    public record ProviderCostSummary(String provider, BigDecimal totalCost, long messageCount) {}
}

