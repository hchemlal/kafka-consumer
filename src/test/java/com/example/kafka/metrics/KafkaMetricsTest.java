package com.example.kafka.metrics;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import io.micrometer.core.instrument.MeterRegistry;

public class KafkaMetricsTest {

    @InjectMocks
    private KafkaMetrics kafkaMetrics;

    @Mock
    private MeterRegistry registry;

    @BeforeEach
    public void init() {
        MockitoAnnotations.openMocks(this);
    }

//    @Test
//    public void testIncrementProcessedMessages() {
//        kafkaMetrics.incrementProcessedMessages();
//
//        assertEquals(1, kafkaMetrics.getProcessedMessagesCounter().count());
//    }
//
//    @Test
//    public void testIncrementFailedMessages() {
//        kafkaMetrics.incrementFailedMessages();
//
//        assertEquals(1, kafkaMetrics.getFailedMessagesCounter().count());
//    }
}
