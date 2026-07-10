package one.formwork.channel.sms.api;

import one.formwork.channel.sms.cost.SmsCostService;
import one.formwork.channel.sms.validation.PhoneNumberValidator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SmsChannelService {

    private final List<SmsGateway> gateways;
    private final SmsChannelProperties properties;
    private final SmsCostService costService;

    public SmsChannelService(List<SmsGateway> gateways, SmsChannelProperties properties,
                             SmsCostService costService) {
        this.gateways = gateways;
        this.properties = properties;
        this.costService = costService;
    }

    public SmsResult sendSms(SmsMessage message) {
        PhoneNumberValidator.validate(message.to());
        SmsGateway gateway = resolveGateway();
        SmsResult result = gateway.send(message);
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

    private SmsGateway resolveGateway() {
        String providerType = properties.getProvider();
        return gateways.stream()
                .filter(g -> g.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No SmsGateway for provider: " + providerType));
    }
}
