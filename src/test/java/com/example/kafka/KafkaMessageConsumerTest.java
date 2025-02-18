package com.example.kafka;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.support.Acknowledgment;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class KafkaMessageConsumerTest {

    @InjectMocks
    private KafkaMessageConsumer kafkaMessageConsumer;

    @Mock
    private Acknowledgment acknowledgment;

    private Set<String> processedKeys; // To simulate caching

    @BeforeEach
    public void init() {
        // Initialize mocks before each test
        MockitoAnnotations.openMocks(this);
        processedKeys = new HashSet<>(); // Initialize the cache
    }

    /**
     * Test successful consumption of a message.
     * Verifies that the acknowledgment is called once after processing the message.
     */
    @Test
    public void testConsume_Success() {
        // Create a mock ConsumerRecord with a sample ActionEvent
        ConsumerRecord<String, ActionEvent> record = new ConsumerRecord<>("my-topic", 0, 0, "key", new ActionEvent());

        // Call the consume method with the mocked record and acknowledgment
        kafkaMessageConsumer.consume(Collections.singletonList(record), acknowledgment, System.currentTimeMillis());

        // Verify that the acknowledgment is called once, indicating successful processing of the message
        verify(acknowledgment, times(1)).acknowledge();
    }

    /**
     * Test consumption of an empty list of records.
     * Verifies that the acknowledgment is never called since there were no records to process.
     */
    @Test
    public void testConsume_EmptyRecords() {
        // Call the consume method with an empty list of records
        kafkaMessageConsumer.consume(Collections.emptyList(), acknowledgment);

        // Verify that acknowledge is never called since there were no records to process
        verify(acknowledgment, never()).acknowledge();
    }

    /**
     * Test deduplication of messages with the same key.
     * Verifies that the acknowledgment is only called once for the first record, and not for the duplicate record.
     */
    @Test
    public void testConsume_Deduplication() {
        // Simulate processing of a record with a unique key
        ConsumerRecord<String, ActionEvent> record1 = new ConsumerRecord<>("my-topic", 0, 0, "uniqueKey", new ActionEvent());
        kafkaMessageConsumer.consume(Collections.singletonList(record1), acknowledgment);
        processedKeys.add(record1.key());

        // Verify acknowledgment for the first record
        verify(acknowledgment, times(1)).acknowledge();

        // Simulate processing of a record with the same key (deduplication)
        ConsumerRecord<String, ActionEvent> record2 = new ConsumerRecord<>("my-topic", 0, 0, "uniqueKey", new ActionEvent());
        kafkaMessageConsumer.consume(Collections.singletonList(record2), acknowledgment);

        // Verify acknowledgment is not called again for the duplicate key
        verify(acknowledgment, times(1)).acknowledge(); // Should still be 1
    }

    /**
     * Test caching of processed records.
     * Verifies that the acknowledgment is only called once for the first record, and not for the cached record.
     */
    @Test
    public void testConsume_Caching() {
        // Simulate processing of a record
        ConsumerRecord<String, ActionEvent> record = new ConsumerRecord<>("my-topic", 0, 0, "cacheKey", new ActionEvent());
        kafkaMessageConsumer.consume(Collections.singletonList(record), acknowledgment);
        processedKeys.add(record.key());

        // Verify acknowledgment for the first record
        verify(acknowledgment, times(1)).acknowledge();

        // Simulate processing of the same record again (should hit the cache)
        kafkaMessageConsumer.consume(Collections.singletonList(record), acknowledgment);

        // Verify acknowledgment is not called again for the cached record
        verify(acknowledgment, times(1)).acknowledge(); // Should still be 1
    }
}
