package one.formwork.channel.sms.validation;

import one.formwork.channel.sms.validation.PhoneNumberValidator.InvalidPhoneNumberException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberValidatorTest {

    @Nested
    class ValidNumbers {

        @ParameterizedTest
        @ValueSource(strings = {
            "+4915112345678",   // Germany mobile
            "+12025551234",     // US
            "+447911123456",    // UK
            "+1234567",         // minimum 7 digits after country code prefix
            "+123456789012345"  // maximum 15 digits total
        })
        void validate_ValidE164Numbers_NoException(String phone) {
            assertDoesNotThrow(() -> PhoneNumberValidator.validate(phone));
        }
    }

    @Nested
    class InvalidNumbers {

        @Test
        void validate_Null_ThrowsInvalidPhoneNumberException() {
            InvalidPhoneNumberException ex = assertThrows(
                    InvalidPhoneNumberException.class,
                    () -> PhoneNumberValidator.validate(null));
            assertTrue(ex.getMessage().contains("null"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "",                     // empty
            "4915112345678",        // missing +
            "+0123456789",          // starts with 0 after +
            "+123456",              // too short (6 digits, need 7+)
            "+1234567890123456",    // too long (16 digits)
            "+49 151 12345678",     // spaces
            "+49-151-12345678",     // hyphens
            "abc",                  // letters
            "+49abc12345678"        // mixed
        })
        void validate_InvalidFormats_ThrowsInvalidPhoneNumberException(String phone) {
            assertThrows(InvalidPhoneNumberException.class,
                    () -> PhoneNumberValidator.validate(phone));
        }

        @Test
        void validate_InvalidNumber_MessageContainsNumber() {
            InvalidPhoneNumberException ex = assertThrows(
                    InvalidPhoneNumberException.class,
                    () -> PhoneNumberValidator.validate("bad-number"));
            assertTrue(ex.getMessage().contains("bad-number"));
            assertTrue(ex.getMessage().contains("E.164"));
        }
    }
}
