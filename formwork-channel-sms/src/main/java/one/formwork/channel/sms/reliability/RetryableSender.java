package one.formwork.channel.sms.reliability;

import one.formwork.channel.sms.api.SmsChannelProperties;
import one.formwork.channel.sms.api.SmsGateway;
import one.formwork.channel.sms.api.SmsMessage;
import one.formwork.channel.sms.api.SmsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Sends through a single gateway with capped, jittered exponential backoff, retrying only
 * transient failures. Deterministic failures (validation, auth/config, most 4xx) are returned
 * immediately: retrying them wastes time and, without idempotency, risks duplicate sends. Finding 7.
 */
public class RetryableSender {

    private static final Logger log = LoggerFactory.getLogger(RetryableSender.class);

    private final int maxAttempts;
    private final long baseMs;
    private final long capMs;
    private final Sleeper sleeper;
    private final DoubleSupplier jitter;

    public RetryableSender(SmsChannelProperties.RetryProperties retry, Sleeper sleeper, DoubleSupplier jitter) {
        SmsChannelProperties.RetryProperties cfg =
                retry != null ? retry : new SmsChannelProperties.RetryProperties();
        this.maxAttempts = Math.max(cfg.getMaxAttempts(), 1);
        this.baseMs = parse(cfg.getBackoff(), Duration.ofSeconds(5)).toMillis();
        this.capMs = parse(cfg.getMaxBackoff(), Duration.ofSeconds(30)).toMillis();
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    public static RetryableSender fromProperties(SmsChannelProperties.RetryProperties retry) {
        return new RetryableSender(retry, Sleeper.real(), () -> ThreadLocalRandom.current().nextDouble());
    }

    public SmsResult send(SmsGateway gateway, SmsMessage message) {
        SmsResult result = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            result = gateway.send(message);
            if (result.isSuccess()) {
                return result;
            }
            if (attempt == maxAttempts || !isRetryable(result)) {
                return result;
            }
            long delay = Backoff.delayMillis(attempt, baseMs, capMs, jitter.getAsDouble());
            log.warn("SMS attempt {}/{} failed on {} (errorCode={}); retrying in {}ms",
                    attempt, maxAttempts, result.provider(), result.errorCode(), delay);
            sleeper.sleep(delay);
        }
        return result;
    }

    /** Transient failures worth retrying: network/timeout, HTTP 429, HTTP 5xx. */
    static boolean isRetryable(SmsResult result) {
        String code = result.errorCode();
        if (code == null) {
            return false;
        }
        if ("SEND_ERROR".equals(code)) {
            return true; // network error / timeout mapped by the gateways
        }
        try {
            int status = Integer.parseInt(code);
            return status == 429 || (status >= 500 && status <= 599);
        } catch (NumberFormatException e) {
            return false; // CONFIG_ERROR, EMPTY_RESPONSE, provider app-errors, 4xx: not retryable
        }
    }

    private static Duration parse(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return DurationStyle.detectAndParse(value);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
