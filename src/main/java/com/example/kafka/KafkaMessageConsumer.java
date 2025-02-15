package com.example.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
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