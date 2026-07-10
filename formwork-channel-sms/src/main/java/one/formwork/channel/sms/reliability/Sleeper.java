package one.formwork.channel.sms.reliability;

/** Indirection over Thread.sleep so retry backoff is testable without real waiting. */
@FunctionalInterface
public interface Sleeper {
    void sleep(long millis);

    static Sleeper real() {
        return millis -> {
            if (millis <= 0) return;
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
