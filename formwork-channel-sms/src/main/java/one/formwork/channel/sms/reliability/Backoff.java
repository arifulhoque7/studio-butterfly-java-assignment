package one.formwork.channel.sms.reliability;

/**
 * Exponential backoff with jitter applied BEFORE the cap, so the cap is always honoured.
 * (Applying jitter after the cap — a common mistake — lets the delay exceed the cap and
 * makes it meaningless.) Jitter is "half jitter": the delay is 50%–100% of the exponential
 * value, which preserves growth while decorrelating retries across callers. Finding 7.
 */
public final class Backoff {

    private Backoff() {}

    /**
     * @param attempt  1-based attempt number
     * @param baseMs   base delay in millis
     * @param capMs    maximum delay in millis (hard ceiling)
     * @param jitter   value in [0,1); pass a random draw in production, a fixed value in tests
     */
    public static long delayMillis(int attempt, long baseMs, long capMs, double jitter) {
        double exponential = baseMs * Math.pow(2, attempt - 1);
        double jittered = exponential * (0.5 + 0.5 * jitter); // jitter first...
        return (long) Math.min(jittered, capMs);              // ...then cap.
    }
}
