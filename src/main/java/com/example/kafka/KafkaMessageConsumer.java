package com.example.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * Main Kafka consumer component that processes incoming messages.
 * Implements reliable message processing with dead letter topic (DLT) support
 * and comprehensive metrics tracking.
 * 
 * Key Features:
 * 1. Manual acknowledgment for reliable processing
 * 2. Dead letter topic handling for failed messages
 * 3. Integration with metrics and monitoring
 * 4. Detailed error handling and logging
 * 
 * Example Usage:
 * <pre>
 * // 1. Basic Consumer Setup
 * // In application.properties:
 * spring.kafka.bootstrap-servers=localhost:9092
 * spring.kafka.consumer.group-id=my-group
 * kafka.topic.name=my-topic
 * kafka.topic.partitions=3
 * kafka.topic.replication-factor=1
 * 
 * // 2. Monitoring Message Processing
 * // View processing metrics:
 * GET /actuator/metrics/kafka.consumer.messages.processed
 * GET /actuator/metrics/kafka.consumer.messages.failed
 * GET /actuator/metrics/kafka.consumer.messages.dlt
 * 
 * // 3. Monitor Processing Times
 * // Check processing latency:
 * GET /actuator/metrics/kafka.consumer.batch.processing
 * {
 *   "name": "kafka.consumer.batch.processing",
 *   "measurements": [
 *     {"statistic": "COUNT", "value": 1000},
 *     {"statistic": "TOTAL_TIME", "value": 123.45},
 *     {"statistic": "MAX", "value": 0.234},
 *     {"statistic": "P95", "value": 0.156}
 *   ]
 * }
 * 
 * // 4. Check DLT Status
 * // Monitor dead letter topic:
 * GET /actuator/metrics/kafka.consumer.messages.dlt
 * 
 * // 5. Producing Test Messages
 * // Using kafka-console-producer:
 * kafka-console-producer.sh --broker-list localhost:9092 --topic my-topic
 * {"timestamp": "2025-02-15T19:00:00", "type": "USER_ACTION"}
 * 
 * // 6. Viewing DLT Messages
 * // Using kafka-console-consumer:
 * kafka-console-consumer.sh --bootstrap-server localhost:9092 \
 *   --topic my-topic.DLT --from-beginning
 * </pre>
 * 
 * Message Flow:
 * 1. Receive message batch from Kafka
 * 2. Process each message individually
 * 3. Call external API with message content
 * 4. Acknowledge successful messages
 * 5. Send failed messages to DLT
 */
@Component
@Slf4j
public class KafkaMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaMessageConsumer.class);
    private static final long MAX_MESSAGE_AGE_SECONDS = 5;
    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                        .withZone(NY_ZONE);
    
    private final ApiService apiService;
    private final EmailService emailService;

    public KafkaMessageConsumer(ApiService apiService, EmailService emailService) {
        this.apiService = apiService;
        this.emailService = emailService;
    }

    /**
     * Main Kafka listener method that processes incoming message batches.
     * Uses manual acknowledgment for reliable message processing.
     * 
     * Processing Steps:
     * 1. Validate batch is not empty
     * 2. Process each message individually
     * 3. Acknowledge after successful processing
     * 4. Handle failures via DLT
     * 
     * @param records Batch of Kafka records to process
     * @param acknowledgment Manual acknowledgment handler
     */
    @KafkaListener(topics = "${kafka.topic.name}", 
                  containerFactory = "kafkaListenerContainerFactory")
    public void consume(List<ConsumerRecord<String, ActionEvent>> records, Acknowledgment acknowledgment) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }

        // Process records one at a time
        for (ConsumerRecord<String, ActionEvent> record : records) {
            String recordKey = getRecordKey(record);

            try {
                // Skip if already processed
                if (isProcessed(record)) {
                    log.debug("Record already processed, skipping: {}", recordKey);
                    // Acknowledge skipped record
                    acknowledgment.acknowledge();
                    continue;
                }

                processMessage(record);
                
                // Mark as processed
                markProcessed(record);
                
                log.info("Successfully processed record: {} with NY timestamp: {}", 
                        recordKey, formatTimestamp(record.value().getTimestamp()));
                
                // Acknowledge successful record
                acknowledgment.acknowledge();
            } catch (Exception e) {
                log.error("Failed to process record: {} with NY timestamp: {}", 
                         recordKey, formatTimestamp(record.value().getTimestamp()), e);
                
                // Send email alert if it's an API failure
                if (e instanceof ApiException) {
                    emailService.sendApiFailureAlert(e.getMessage(), record, e);
                }
                
                // Acknowledge the failed record and throw to trigger DLT
                acknowledgment.acknowledge();
                throw e; // Spring Kafka will handle sending to DLT
            }
        }
    }

    @Cacheable(value = "processedRecords", key = "#record.topic + '-' + #record.partition + '-' + #record.offset")
    public boolean isProcessed(ConsumerRecord<String, ActionEvent> record) {
        return false; // Cache will intercept and return true if exists
    }

    @CachePut(value = "processedRecords", key = "#record.topic + '-' + #record.partition + '-' + #record.offset")
    public boolean markProcessed(ConsumerRecord<String, ActionEvent> record) {
        return true;
    }

    private String getRecordKey(ConsumerRecord<String, ActionEvent> record) {
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    /**
     * Handles messages that failed processing and were sent to DLT.
     * Provides final error handling and logging for failed messages.
     * 
     * Actions:
     * 1. Log detailed error information
     * 2. Update DLT metrics
     * 3. Perform any necessary cleanup
     * 
     * @param record Failed record from DLT
     * @param acknowledgment Manual acknowledgment handler
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, ActionEvent> record, 
                         @Header(KafkaHeaders.ORIGINAL_EXCEPTION) Exception e,
                         Acknowledgment acknowledgment) {
        log.error("Processing failed for record in topic {} with NY timestamp: {}. Error: {}", 
                 record.topic(), formatTimestamp(record.value().getTimestamp()), e.getMessage());
        
        try {
            String errorInfo = String.format(
                "{\"originalTopic\":\"%s\",\"timestamp\":\"%s\",\"error\":\"%s\",\"stackTrace\":\"%s\"}",
                record.topic(),
                formatTimestamp(record.value().getTimestamp()),
                e.getMessage().replace("\"", "\\\""),
                getStackTraceAsString(e).replace("\"", "\\\"")
            );
            
            log.info("Record sent to DLT: {}", errorInfo);
        } catch (Exception ex) {
            log.error("Error logging DLT record: {}", ex.getMessage());
        }
        
        // Acknowledge the DLT record
        acknowledgment.acknowledge();
    }

    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    private String formatTimestamp(String timestamp) {
        return ZonedDateTime.parse(timestamp)
                          .withZoneSameInstant(NY_ZONE)
                          .format(TIMESTAMP_FORMATTER);
    }

    /**
     * Processes an individual Kafka message.
     * Implements message validation and API interaction.
     * 
     * Validation Rules:
     * 1. Check message age
     * 2. Validate message content
     * 3. Verify API response
     * 
     * @param record Single Kafka record to process
     * @throws MessageTooOldException if message exceeds age limit
     * @throws ApiException if API call fails
     */
    private void processMessage(ConsumerRecord<String, ActionEvent> record) {
        String nyTimestamp = formatTimestamp(record.value().getTimestamp());
        log.info("Processing record with key: {} and NY timestamp: {}", 
                record.key(), nyTimestamp);
        
        // Parse timestamp and check age
        ZonedDateTime messageTime = ZonedDateTime.parse(record.value().getTimestamp())
                                               .withZoneSameInstant(NY_ZONE);
        ZonedDateTime now = ZonedDateTime.now(NY_ZONE);
        
        long ageSeconds = ChronoUnit.SECONDS.between(messageTime, now);
        if (ageSeconds > MAX_MESSAGE_AGE_SECONDS) {
            throw new ApiException(
                String.format("Message is too old. Age: %d seconds, Max allowed: %d seconds (NY time)", 
                            ageSeconds, MAX_MESSAGE_AGE_SECONDS),
                null
            );
        }
        
        boolean success = apiService.postMessage(nyTimestamp);
        if (!success) {
            throw new ApiException("API call returned unsuccessful status", null);
        }
    }
}