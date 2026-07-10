package one.formwork.channel.sms.provider;

import one.formwork.channel.sms.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/**
 * SMS gateway using the AWS SNS Publish API via direct HTTP (no SDK dependency).
 * Uses AWS Signature Version 4 for authentication.
 * Requires AWS credentials via environment variables AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY.
 */
public class AwsSnsSmsGateway implements SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(AwsSnsSmsGateway.class);
    private static final String SERVICE = "sns";
    private static final DateTimeFormatter AMZ_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_STAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WebClient webClient;
    private final SmsChannelProperties.AwsSnsProperties config;

    public AwsSnsSmsGateway(SmsChannelProperties.AwsSnsProperties config) {
        this.config = config;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public SmsResult send(SmsMessage message) {
        String region = config.getRegion();
        String host = "sns." + region + ".amazonaws.com";
        String endpoint = "https://" + host;

        TreeMap<String, String> params = new TreeMap<>();
        params.put("Action", "Publish");
        params.put("Message", message.body());
        params.put("PhoneNumber", message.to());
        params.put("Version", "2010-03-31");
        if (config.getSenderId() != null && !config.getSenderId().isBlank()) {
            params.put("MessageAttributes.entry.1.Name", "AWS.SNS.SMS.SenderID");
            params.put("MessageAttributes.entry.1.Value.DataType", "String");
            params.put("MessageAttributes.entry.1.Value.StringValue", config.getSenderId());
        }

        String queryString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        try {
            String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
            String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");
            if (accessKey == null || secretKey == null) {
                return SmsResult.failure("AWS_SNS", "CONFIG_ERROR",
                        "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables required");
            }

            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            String amzDate = now.format(AMZ_DATE_FMT);
            String dateStamp = now.format(DATE_STAMP_FMT);

            String canonicalHeaders = "host:" + host + "\nx-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-date";
            String payloadHash = sha256Hex("");
            String canonicalRequest = "GET\n/\n" + queryString + "\n" + canonicalHeaders + "\n"
                    + signedHeaders + "\n" + payloadHash;

            String credentialScope = dateStamp + "/" + region + "/" + SERVICE + "/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + credentialScope + "\n"
                    + sha256Hex(canonicalRequest);

            byte[] signingKey = deriveSigningKey(secretKey, dateStamp, region, SERVICE);
            String signature = hexEncode(hmacSha256(signingKey, stringToSign));
            String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                    + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

            String responseBody = webClient.get()
                    .uri(endpoint + "/?" + queryString)
                    .header("Authorization", authorization)
                    .header("x-amz-date", amzDate)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // SNS returns XML; extract MessageId
            String messageId = extractXmlElement(responseBody, "MessageId");
            log.info("AWS SNS SMS sent: messageId={}, to={}", messageId, message.to());
            return SmsResult.success(messageId, "AWS_SNS", 1);
        } catch (WebClientResponseException e) {
            log.error("AWS SNS API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return SmsResult.failure("AWS_SNS", String.valueOf(e.getStatusCode().value()), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("AWS SNS SMS send failed: {}", e.getMessage(), e);
            return SmsResult.failure("AWS_SNS", "SEND_ERROR", e.getMessage());
        }
    }

    @Override
    public boolean supports(String providerType) {
        return "AWS_SNS".equalsIgnoreCase(providerType);
    }

    @Override
    public String getProviderName() {
        return "AWS_SNS";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hexEncode(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static byte[] deriveSigningKey(String secret, String dateStamp, String region, String service) {
        byte[] kDate = hmacSha256(("AWS4" + secret).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String extractXmlElement(String xml, String elementName) {
        if (xml == null) return null;
        String startTag = "<" + elementName + ">";
        String endTag = "</" + elementName + ">";
        int start = xml.indexOf(startTag);
        int end = xml.indexOf(endTag);
        if (start >= 0 && end > start) {
            return xml.substring(start + startTag.length(), end);
        }
        return null;
    }
}
