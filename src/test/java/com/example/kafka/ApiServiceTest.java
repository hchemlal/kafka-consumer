package com.example.kafka;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ApiServiceTest {

    @InjectMocks
    private ApiService apiService;

    @Mock
    private ExternalApiClient externalApiClient; // Assuming you have this client

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testPostMessage_Success() {
        String message = "Test Message";
        when(externalApiClient.sendMessage(message)).thenReturn(true);

        boolean result = apiService.postMessage(message);
        assertTrue(result);
        verify(externalApiClient, times(1)).sendMessage(message);
    }

    @Test
    public void testPostMessage_Failure() {
        String message = "Test Message";
        when(externalApiClient.sendMessage(message)).thenReturn(false);

        boolean result = apiService.postMessage(message);
        assertFalse(result);
        verify(externalApiClient, times(1)).sendMessage(message);
    }
}
