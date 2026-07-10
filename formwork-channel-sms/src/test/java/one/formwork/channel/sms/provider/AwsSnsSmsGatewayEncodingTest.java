package one.formwork.channel.sms.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the AWS SNS SigV4 signing defect (REVIEW.md Finding 2).
 *
 * <p>AWS Signature Version 4 requires the canonical query string to be encoded per RFC 3986:
 * a space must be {@code %20}, {@code ~} stays literal, and {@code *} becomes {@code %2A}.
 * The original {@code encode} used {@code URLEncoder} (application/x-www-form-urlencoded),
 * which renders a space as {@code +} and escapes {@code ~}. AWS re-canonicalizes with RFC 3986
 * server-side, so the signatures never match and every spaced message returns
 * {@code 403 SignatureDoesNotMatch}.
 *
 * <p>These assertions are RED on the original code and GREEN after the RFC 3986 fix.
 */
class AwsSnsSmsGatewayEncodingTest {

    @Test
    void encode_space_usesPercent20_notPlus() {
        String encoded = AwsSnsSmsGateway.encode("Hello world");
        assertEquals("Hello%20world", encoded, "SigV4 requires %20 for space, not +");
        assertFalse(encoded.contains("+"), "a '+' in the signed query breaks the signature");
    }

    @Test
    void encode_tilde_isUnreservedAndNotEscaped() {
        assertEquals("~", AwsSnsSmsGateway.encode("~"),
                "'~' is an RFC 3986 unreserved character and must stay literal");
    }

    @Test
    void encode_asterisk_isPercentEncoded() {
        assertEquals("%2A", AwsSnsSmsGateway.encode("*"),
                "'*' is not unreserved under RFC 3986 and must be percent-encoded");
    }

    @Test
    void encode_plusSign_isPercentEncoded() {
        assertEquals("%2B", AwsSnsSmsGateway.encode("+"),
                "a literal '+' (e.g. E.164 prefix) must be %2B");
    }
}
