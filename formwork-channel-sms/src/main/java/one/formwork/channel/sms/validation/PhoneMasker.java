package one.formwork.channel.sms.validation;

/** Masks phone numbers for logging/persistence so raw MSISDNs never leak. Finding 3. */
public final class PhoneMasker {

    private PhoneMasker() {}

    /** {@code +491234567890 -> +491***90}; short/null inputs collapse to {@code ***}. */
    public static String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return "***";
        }
        return phoneNumber.substring(0, 4) + "***" + phoneNumber.substring(phoneNumber.length() - 2);
    }
}
