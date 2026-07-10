package one.formwork.channel.sms.api;

import one.formwork.channel.sms.validation.PhoneNumberValidator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SmsChannelService {

    private final List<SmsGateway> gateways;
    private final SmsChannelProperties properties;

    public SmsChannelService(List<SmsGateway> gateways, SmsChannelProperties properties) {
        this.gateways = gateways;
        this.properties = properties;
    }

    public SmsResult sendSms(SmsMessage message) {
        PhoneNumberValidator.validate(message.to());
        SmsGateway gateway = resolveGateway();
        return gateway.send(message);
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
