package one.formwork.channel.sms.provider;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import one.formwork.channel.sms.api.SmsChannelProperties;
import one.formwork.channel.sms.api.SmsMessage;
import one.formwork.channel.sms.api.SmsResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one honest integration test (Part 3.4): drives the Twilio gateway over real HTTP against a
 * stub server and asserts on the actual bytes sent — method, path, headers, and body. Unlike the
 * existing {@code *WireMockTest} classes (which mock the WebClient chain and assert nothing about
 * the request), this fails if the URL, auth header, content-type, or form encoding regresses.
 */
class TwilioSmsGatewayIntegrationTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    @Test
    void send_writesExpectedRequestBytesOnTheWire() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"sid\":\"SM123\",\"num_segments\":\"2\"}"));

        SmsChannelProperties.TwilioProperties config = new SmsChannelProperties.TwilioProperties();
        config.setAccountSid("AC123");
        config.setAuthToken("tok");
        config.setFromNumber("+15551234567");
        TwilioSmsGateway gateway =
                new TwilioSmsGateway(config, server.url("/2010-04-01").toString());

        SmsResult result = gateway.send(
                new SmsMessage("+4915112345678", "Hello world", UUID.randomUUID()));

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getPath().endsWith("/Accounts/AC123/Messages.json"),
                "path was: " + request.getPath());
        assertTrue(request.getHeader("Content-Type").startsWith("application/x-www-form-urlencoded"));

        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("AC123:tok".getBytes());
        assertEquals(expectedAuth, request.getHeader("Authorization"));

        String body = request.getBody().readUtf8();
        assertTrue(body.contains("To=%2B4915112345678"), body);
        assertTrue(body.contains("From=%2B15551234567"), body);
        assertTrue(body.contains("Body=Hello+world"), body); // form-encoded space is '+'

        assertTrue(result.isSuccess());
        assertEquals("SM123", result.messageId());
        assertEquals(2, result.segmentCount());
    }
}
