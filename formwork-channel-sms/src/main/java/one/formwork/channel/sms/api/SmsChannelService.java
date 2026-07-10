package one.formwork.channel.sms.api;

import one.formwork.channel.sms.cost.SmsCostService;
import one.formwork.channel.sms.reliability.RetryableSender;
import one.formwork.channel.sms.reliability.Sleeper;
import one.formwork.channel.sms.validation.PhoneNumberValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

@Service
public class SmsChannelService {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelService.class);

    private final List<SmsGateway> gateways;
    private final SmsChannelProperties properties;
    private final SmsCostService costService;
    private final Sleeper sleeper;
    private final DoubleSupplier jitter;

    public SmsChannelService(List<SmsGateway> gateways, SmsChannelProperties properties,
                             SmsCostService costService) {
        this(gateways, properties, costService, Sleeper.real(),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    // Test-visible: inject a no-op sleeper and fixed jitter so retry/backoff runs without real waiting.
    SmsChannelService(List<SmsGateway> gateways, SmsChannelProperties properties,
                      SmsCostService costService, Sleeper sleeper, DoubleSupplier jitter) {
        this.gateways = gateways;
        this.properties = properties;
        this.costService = costService;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    public SmsResult sendSms(SmsMessage message) {
        PhoneNumberValidator.validate(message.to());

        RetryableSender sender = new RetryableSender(retryConfig(), sleeper, jitter);
        SmsResult result = null;
        for (String provider : providerChain(message.tenantId())) {
            SmsGateway gateway = gatewayFor(provider);
            if (gateway == null) {
                log.warn("No SmsGateway available for provider {}; skipping in failover chain", provider);
                continue;
            }
            result = sender.send(gateway, message);
            if (result.isSuccess()) {
                break;
            }
            log.warn("Provider {} failed (errorCode={}); trying next in failover chain",
                    provider, result.errorCode());
        }

        if (result == null) {
            throw new IllegalStateException(
                    "No SmsGateway for provider chain: " + providerChain(message.tenantId()));
        }
        if (result.isSuccess()) {
            // Record cost on the request thread so the tenant context/transaction propagate. Finding 1.
            costService.recordCost(message.tenantId(), message.to(), result);
        }
        return result;
    }

    public List<SmsResult> sendBulk(List<SmsMessage> messages) {
        return messages.stream().map(this::sendSms).toList();
    }

    public void handleDeliveryCallback(String provider, Map<String, String> params) {
        // Provider-specific callback handling
    }

    /** Ordered, de-duplicated provider chain: tenant/global primary first, then configured failover. */
    private List<String> providerChain(UUID tenantId) {
        LinkedHashSet<String> chain = new LinkedHashSet<>();
        chain.add(primaryProvider(tenantId));
        List<String> failover = properties.getFailover();
        if (failover != null) {
            chain.addAll(failover);
        }
        return new ArrayList<>(chain);
    }

    /** Tenant override if present and non-blank, else the global default. Tenants never affect each other. */
    private String primaryProvider(UUID tenantId) {
        Map<String, String> overrides = properties.getTenantProviders();
        if (tenantId != null && overrides != null) {
            String provider = overrides.get(tenantId.toString());
            if (provider != null && !provider.isBlank()) {
                return provider;
            }
        }
        return properties.getProvider();
    }

    private SmsGateway gatewayFor(String providerType) {
        return gateways.stream()
                .filter(g -> g.supports(providerType))
                .findFirst()
                .orElse(null);
    }

    private SmsChannelProperties.RetryProperties retryConfig() {
        return properties.getRetry() != null
                ? properties.getRetry()
                : new SmsChannelProperties.RetryProperties();
    }
}
