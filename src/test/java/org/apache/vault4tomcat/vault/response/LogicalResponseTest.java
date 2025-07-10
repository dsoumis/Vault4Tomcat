package org.apache.vault4tomcat.vault.response;

import org.apache.vault4tomcat.vault.rest.RestResponse;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LogicalResponseTest {

    @Test
    void testParseValidKVv2Response() {
        String json = """
            {
              "data": {
                "data": {
                  "username": "admin",
                  "password": "secret123"
                },
                "metadata": {
                  "created_time": "2023-01-01T00:00:00Z"
                }
              }
            }
        """;

        RestResponse mockResponse = mock(RestResponse.class);
        when(mockResponse.body()).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        LogicalResponse response = new LogicalResponse(mockResponse, "readV2");
        Map<String, String> data = response.getData();

        assertEquals(2, data.size());
        assertEquals("admin", data.get("username"));
        assertEquals("secret123", data.get("password"));
    }

    @Test
    void testResponseWithMissingMetadata() {
        String json = """
            {
              "data": {
                "data": {
                  "apiKey": "value123"
                }
              }
            }
        """;

        RestResponse mockResponse = mock(RestResponse.class);
        when(mockResponse.body()).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        LogicalResponse response = new LogicalResponse(mockResponse, "readV2");
        Map<String, String> data = response.getData();

        assertEquals(1, data.size());
        assertEquals("value123", data.get("apiKey"));
    }

    @Test
    void testResponseWithEmptyData() {
        String json = """
            {
              "data": {
                "data": {}
              }
            }
        """;

        RestResponse mockResponse = mock(RestResponse.class);
        when(mockResponse.body()).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        LogicalResponse response = new LogicalResponse(mockResponse, "readV2");
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testMalformedJsonDoesNotCrash() {
        String json = "{ invalid json }";

        RestResponse mockResponse = mock(RestResponse.class);
        when(mockResponse.body()).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        LogicalResponse response = new LogicalResponse(mockResponse, "readV2");
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testNullValueInDataIsIgnored() {
        String json = """
            {
              "data": {
                "data": {
                  "valid": "ok",
                  "nullValue": null
                }
              }
            }
        """;

        RestResponse mockResponse = mock(RestResponse.class);
        when(mockResponse.body()).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        LogicalResponse response = new LogicalResponse(mockResponse, "readV2");
        Map<String, String> data = response.getData();

        assertEquals(1, data.size());
        assertEquals("ok", data.get("valid"));
        assertNull(data.get("nullValue"));
    }
}
