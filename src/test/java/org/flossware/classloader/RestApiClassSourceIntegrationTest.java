package org.flossware.classloader;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for RestApiClassSource using MockWebServer.
 * Tests actual HTTP communication without requiring a real REST API.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class RestApiClassSourceIntegrationTest {

    private MockWebServer server;
    private RestApiClassSource source;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    @DisplayName("Should load class data from REST API with binary response")
    void testLoadClassDataBinaryResponse() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 1, 2, 3, 4};

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(new okio.Buffer().write(classData)));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .responseFormat(RestApiClassSource.ResponseFormat.BINARY)
            .build();

        byte[] result = source.loadClassData("com.example.TestClass");

        assertArrayEquals(classData, result);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a request to the mock server");
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains("TestClass.class"));
    }

    @Test
    @DisplayName("Should load class data with Base64 JSON response using data field")
    void testLoadClassDataBase64JsonResponse() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 5, 6, 7, 8};
        String base64Data = Base64.getEncoder().encodeToString(classData);
        String jsonResponse = "{\"data\":\"" + base64Data + "\"}";

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(jsonResponse)
            .setHeader("Content-Type", "application/json"));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/api/").toString())
            .responseFormat(RestApiClassSource.ResponseFormat.BASE64_JSON_FIELD)
            .build();

        byte[] result = source.loadClassData("com.example.TestClass");

        assertArrayEquals(classData, result);
    }

    @Test
    @DisplayName("Should include custom headers in request")
    void testCustomHeaders() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 9, 10};

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(new okio.Buffer().write(classData)));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .addHeader("X-API-Key", "secret-key-123")
            .addHeader("X-Custom-Header", "custom-value")
            .build();

        source.loadClassData("TestClass");

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a request to the mock server");
        assertEquals("secret-key-123", request.getHeader("X-API-Key"));
        assertEquals("custom-value", request.getHeader("X-Custom-Header"));
    }

    @Test
    @DisplayName("Should include query parameters in URL")
    void testQueryParameters() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(new okio.Buffer().write(classData)));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .addQueryParam("version", "1.0")
            .addQueryParam("format", "binary")
            .build();

        source.loadClassData("TestClass");

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a request to the mock server");
        String path = request.getPath();
        assertTrue(path.contains("version=1.0"));
        assertTrue(path.contains("format=binary"));
    }

    @Test
    @DisplayName("Should include Basic auth header")
    void testBasicAuthentication() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(new okio.Buffer().write(classData)));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .auth(AuthConfig.basic("admin", "password123"))
            .build();

        source.loadClassData("TestClass");

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a request to the mock server");
        String authHeader = request.getHeader("Authorization");
        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Basic "));

        // Verify the credentials are correctly encoded
        String encodedCredentials = authHeader.substring("Basic ".length());
        String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials));
        assertEquals("admin:password123", decodedCredentials);
    }

    @Test
    @DisplayName("Should include Bearer token auth header")
    void testBearerAuthentication() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(new okio.Buffer().write(classData)));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .auth(AuthConfig.bearer("my-secret-token"))
            .build();

        source.loadClassData("TestClass");

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a request to the mock server");
        String authHeader = request.getHeader("Authorization");
        assertEquals("Bearer my-secret-token", authHeader);
    }

    @Test
    @DisplayName("Should throw IOException on HTTP 404")
    void testHttp404Error() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody("Not Found"));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .build();

        IOException exception = assertThrows(IOException.class, () -> {
            source.loadClassData("MissingClass");
        });

        assertTrue(exception.getMessage().contains("404"));
    }

    @Test
    @DisplayName("Should throw IOException on HTTP 401 Unauthorized")
    void testHttp401Error() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody("Unauthorized"));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .build();

        IOException exception = assertThrows(IOException.class, () -> {
            source.loadClassData("SecureClass");
        });

        assertTrue(exception.getMessage().contains("401"));
    }

    @Test
    @DisplayName("Should throw IOException on HTTP 500 Server Error")
    void testHttp500Error() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .build();

        IOException exception = assertThrows(IOException.class, () -> {
            source.loadClassData("ErrorClass");
        });

        assertTrue(exception.getMessage().contains("500"));
    }

    @Test
    @DisplayName("Should use custom class path template")
    void testCustomClassPathTemplate() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(new okio.Buffer().write(classData)));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .classPathTemplate("api/v1/classes/{fullclass}")
            .build();

        source.loadClassData("com.example.TestClass");

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a request to the mock server");
        String path = request.getPath();
        assertTrue(path.contains("api/v1/classes/com/example/TestClass"));
    }

    @Test
    @DisplayName("Should handle DIRECT response format same as BINARY")
    void testDirectResponseFormat() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(new okio.Buffer().write(classData)));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .responseFormat(RestApiClassSource.ResponseFormat.DIRECT)
            .build();

        byte[] result = source.loadClassData("TestClass");

        assertArrayEquals(classData, result);
    }

    @Test
    @DisplayName("Should convert package separator in class name to URL path")
    void testPackageSeparatorConversion() throws Exception {
        byte[] classData = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(new okio.Buffer().write(classData)));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .build();

        source.loadClassData("com.example.util.StringHelper");

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a request to the mock server");
        String path = request.getPath();
        assertTrue(path.contains("com/example/util/StringHelper.class"));
    }

    @Test
    @DisplayName("Should check if class can be loaded via HEAD request")
    void testCanLoadClass() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(200));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .enableCanLoadCheck(true)
            .build();

        boolean result = source.canLoad("TestClass");

        assertTrue(result);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "Expected a HEAD request to the mock server");
        assertEquals("HEAD", request.getMethod());
    }

    @Test
    @DisplayName("Should return false for canLoad on 404")
    void testCanLoadClassNotFound() throws Exception {
        server.enqueue(new MockResponse()
            .setResponseCode(404));

        source = RestApiClassSource.builder()
            .baseUrl(server.url("/").toString())
            .enableCanLoadCheck(true)
            .build();

        boolean result = source.canLoad("MissingClass");

        assertFalse(result);
    }
}
