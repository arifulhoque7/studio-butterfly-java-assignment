package one.formwork.channel.sms.validation;

import java.util.regex.Pattern;

public final class PhoneNumberValidator {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private PhoneNumberValidator() {}

    public static void validate(String phoneNumber) {
        if (phoneNumber == null || !E164.matcher(phoneNumber).matches()) {
            throw new InvalidPhoneNumberException(
                    "Invalid phone number (must be E.164 format): " + phoneNumber);
        }
    }

    public static class InvalidPhoneNumberException extends RuntimeException {
        public InvalidPhoneNumberException(String message) { super(message); }
    }
}
