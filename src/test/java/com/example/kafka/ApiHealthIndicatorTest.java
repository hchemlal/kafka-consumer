package com.example.kafka.health;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.Health;

public class ApiHealthIndicatorTest {

    @InjectMocks
    private ApiHealthIndicator apiHealthIndicator;

    @Mock
    private SomeDependency someDependency; // Mock any dependencies

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testHealthUp() {
        when(someDependency.isHealthy()).thenReturn(true);

        Health health = apiHealthIndicator.health();
        assertEquals(Health.up().build(), health);
    }

    @Test
    public void testHealthDown() {
        when(someDependency.isHealthy()).thenReturn(false);

        Health health = apiHealthIndicator.health();
        assertEquals(Health.down().build(), health);
    }
}
