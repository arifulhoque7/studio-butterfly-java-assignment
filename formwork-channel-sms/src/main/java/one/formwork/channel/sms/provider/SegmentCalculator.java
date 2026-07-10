package one.formwork.channel.sms.provider;

/**
 * Computes the number of SMS segments a body occupies, for providers that do not
 * return a segment count in their response (AWS SNS, MessageBird, BudgetSMS).
 * Carriers bill per segment, so a hardcoded "1" under-bills multi-part messages. Finding 5.
 *
 * <p>GSM 03.38: 160 chars single / 153 per part; extension chars cost 2.
 * Any character outside GSM-7 forces UCS-2: 70 single / 67 per part.
 */
public final class SegmentCalculator {

    private static final String GSM7_BASIC =
            "@£$¥èéùìòÇ\nØø\rÅå"
          + "Δ_ΦΓΛΩΠΨΣΘΞÆæßÉ"
          + " !\"#¤%&'()*+,-./0123456789:;<=>?"
          + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
          + "¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String GSM7_EXTENDED = "^{}\\[~]|€";

    private SegmentCalculator() {}

    public static int segments(String body) {
        if (body == null || body.isEmpty()) {
            return 1;
        }
        int gsmLength = gsmSeptets(body);
        if (gsmLength >= 0) {
            return gsmLength <= 160 ? 1 : (int) Math.ceil(gsmLength / 153.0);
        }
        int units = body.length(); // UCS-2 code units
        return units <= 70 ? 1 : (int) Math.ceil(units / 67.0);
    }

    /** Septet count under GSM-7, or -1 if the body needs UCS-2. */
    private static int gsmSeptets(String body) {
        int length = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (GSM7_EXTENDED.indexOf(c) >= 0) {
                length += 2;
            } else if (GSM7_BASIC.indexOf(c) >= 0) {
                length += 1;
            } else {
                return -1;
            }
        }
        return length;
    }
}
