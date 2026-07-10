package one.formwork.channel.sms.cost;

import java.util.UUID;
import one.formwork.channel.sms.api.DeliveryStatus;
import one.formwork.channel.sms.api.SmsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsCostServiceTest {
    private final UUID tenantId = UUID.randomUUID();

    @Mock
    private SmsCostRepository repository;

    @Mock
    private ProviderRateRegistry rateRegistry;

    private SmsCostService service;

    @BeforeEach
    void setUp() {
        service = new SmsCostService(repository, rateRegistry);
    }

    @Nested
    class RecordCost {

        @Test
        void recordCost_SuccessfulSms_SavesCostRecord() {
            SmsResult result = SmsResult.success("msg-1", "TWILIO", 2);
            when(rateRegistry.getRate("TWILIO", "DE")).thenReturn(new BigDecimal("0.075"));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SmsCostEntity saved = service.recordCost(tenantId, "+491234567890", result);

            assertNotNull(saved);
            assertEquals("TWILIO", saved.getProvider());
            assertEquals(2, saved.getSegmentCount());
            assertEquals(new BigDecimal("0.150"), saved.getTotalCost());
            assertEquals("EUR", saved.getCurrency());
            assertEquals("DE", saved.getCountryCode());
        }

        @Test
        void recordCost_FailedSms_ReturnsNull() {
            SmsResult result = SmsResult.failure("TWILIO", "500", "Server Error");

            SmsCostEntity saved = service.recordCost(tenantId, "+491234567890", result);

            assertNull(saved);
            verify(repository, never()).save(any());
        }

        @Test
        void recordCost_ZeroSegments_DefaultsToOne() {
            SmsResult result = new SmsResult("msg-1", "TWILIO", DeliveryStatus.ACCEPTED, 0, null, null, null);
            when(rateRegistry.getRate("TWILIO", "DE")).thenReturn(new BigDecimal("0.075"));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SmsCostEntity saved = service.recordCost(tenantId, "+491234567890", result);

            assertEquals(1, saved.getSegmentCount());
            assertEquals(new BigDecimal("0.075"), saved.getTotalCost());
        }

        @Test
        void recordCost_MasksRecipient() {
            SmsResult result = SmsResult.success("msg-1", "TWILIO", 1);
            when(rateRegistry.getRate(any(), any())).thenReturn(new BigDecimal("0.07"));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SmsCostEntity saved = service.recordCost(tenantId, "+491234567890", result);

            ArgumentCaptor<SmsCostEntity> captor = ArgumentCaptor.forClass(SmsCostEntity.class);
            verify(repository).save(captor.capture());
            String masked = captor.getValue().getRecipient();
            assertFalse(masked.contains("1234567890"));
            assertTrue(masked.startsWith("+491"));
        }
    }

    @Nested
    class GetMonthlyCost {

        @Test
        void getMonthlyCost_HasCosts_ReturnsSum() {
            when(repository.sumCostByTenantAndPeriod(eq(tenantId), any(), any()))
                    .thenReturn(new BigDecimal("12.50"));

            BigDecimal cost = service.getMonthlyCost(tenantId, YearMonth.of(2026, 3));

            assertEquals(new BigDecimal("12.50"), cost);
        }

        @Test
        void getMonthlyCost_NoCosts_ReturnsZero() {
            when(repository.sumCostByTenantAndPeriod(eq(tenantId), any(), any())).thenReturn(null);

            BigDecimal cost = service.getMonthlyCost(tenantId, YearMonth.of(2026, 3));

            assertEquals(BigDecimal.ZERO, cost);
        }
    }

    @Nested
    class GetCostBreakdown {

        @Test
        void getCostBreakdown_MultipleProviders_ReturnsMap() {
            List<Object[]> rows = List.of(
                    new Object[]{"TWILIO", new BigDecimal("10.00"), 100L},
                    new Object[]{"VONAGE", new BigDecimal("5.00"), 80L}
            );
            when(repository.costBreakdownByProvider(eq(tenantId), any(), any())).thenReturn(rows);

            Map<String, SmsCostService.ProviderCostSummary> breakdown =
                    service.getCostBreakdown(tenantId, Instant.now().minusSeconds(86400), Instant.now());

            assertEquals(2, breakdown.size());
            assertEquals(new BigDecimal("10.00"), breakdown.get("TWILIO").totalCost());
            assertEquals(80L, breakdown.get("VONAGE").messageCount());
        }

        @Test
        void getCostBreakdown_Empty_ReturnsEmptyMap() {
            when(repository.costBreakdownByProvider(eq(tenantId), any(), any())).thenReturn(List.of());

            Map<String, SmsCostService.ProviderCostSummary> breakdown =
                    service.getCostBreakdown(tenantId, Instant.now().minusSeconds(86400), Instant.now());

            assertTrue(breakdown.isEmpty());
        }
    }

    @Nested
    class ExtractCountryCode {

        @ParameterizedTest
        @CsvSource({
            "+491234567890,DE",
            "+431234567890,AT",
            "+411234567890,CH",
            "+441234567890,GB",
            "+11234567890,US",
            "+331234567890,FR",
            "+391234567890,IT",
            "+341234567890,ES",
            "+311234567890,NL",
            "00491234567890,DE"
        })
        void extractCountryCode_KnownPrefixes_ReturnsCode(String phone, String expected) {
            assertEquals(expected, SmsCostService.extractCountryCode(phone));
        }

        @Test
        void extractCountryCode_UnknownPrefix_ReturnsPrefixDigits() {
            String code = SmsCostService.extractCountryCode("+901234567890");
            assertNotNull(code);
            assertFalse(code.isEmpty());
        }

        @Test
        void extractCountryCode_ShortNumber_ReturnsXX() {
            assertEquals("XX", SmsCostService.extractCountryCode("12"));
        }

        @Test
        void extractCountryCode_Null_ReturnsXX() {
            assertEquals("XX", SmsCostService.extractCountryCode(null));
        }
    }

    @Nested
    class MaskRecipient {

        @Test
        void maskRecipient_Normal_MasksMiddle() {
            String masked = SmsCostService.maskRecipient("+491234567890");
            assertEquals("+491***90", masked);
        }

        @Test
        void maskRecipient_Short_ReturnsStars() {
            assertEquals("***", SmsCostService.maskRecipient("123"));
        }

        @Test
        void maskRecipient_Null_ReturnsStars() {
            assertEquals("***", SmsCostService.maskRecipient(null));
        }
    }
}

