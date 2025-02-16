package com.example.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Kafka consumer settings.
 * Sets up reliable message processing with manual acknowledgment
 * and dead letter topic support.
 * 
 * Features:
 * 1. Manual acknowledgment mode
 * 2. JSON message deserialization
 * 3. Concurrent message processing
 * 4. Dead letter topic configuration
 * 5. Consumer group management
 * 
 * Example Usage:
 * <pre>
 * // 1. Basic Configuration
 * // In application.properties:
 * spring.kafka.bootstrap-servers=localhost:9092
 * spring.kafka.consumer.group-id=my-group
 * kafka.topic.name=my-topic
 * kafka.topic.partitions=3
 * kafka.topic.replication-factor=1
 * 
 * // 2. Custom Consumer Configuration
 * @Configuration
 * public class CustomKafkaConfig extends KafkaConsumerConfig {
 *     @Bean
 *     @Override
 *     public ConsumerFactory<String, CustomEvent> consumerFactory() {
 *         Map<String, Object> props = new HashMap<>();
 *         props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);
 *         props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
 *         // ... other custom settings ...
 *         return new DefaultKafkaConsumerFactory<>(props);
 *     }
 * }
 * 
 * // 3. Custom Error Handler
 * @Bean
 * public DefaultErrorHandler errorHandler() {
 *     return new DefaultErrorHandler((record, exception) -> {
 *         // Custom error handling logic
 *         log.error("Error processing record: {}", record, exception);
 *     });
 * }
 * 
 * // 4. Monitoring Consumer Groups
 * // Using kafka-consumer-groups command:
 * kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
 *   --describe --group my-group
 * 
 * // 5. Performance Tuning
 * // Adjust these properties for optimal performance:
 * spring.kafka.consumer.fetch-min-size=1KB
 * spring.kafka.consumer.fetch-max-wait=500ms
 * spring.kafka.consumer.max-poll-records=500
 * spring.kafka.consumer.heartbeat-interval=3000ms
 * 
 * // 6. Security Configuration
 * spring.kafka.security.protocol=SSL
 * spring.kafka.ssl.key-store-location=file:/path/to/keystore
 * spring.kafka.ssl.key-store-password=keystore-pass
 * spring.kafka.ssl.trust-store-location=file:/path/to/truststore
 * spring.kafka.ssl.trust-store-password=truststore-pass
 * </pre>
 * 
 * The configuration prioritizes:
 * - Reliability (manual acks, DLT)
 * - Performance (concurrent processing)
 * - Monitoring (error handling)
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${kafka.topic.name}")
    private String topicName;

    @Value("${kafka.topic.partitions:3}")
    private int topicPartitions;

    @Value("${kafka.topic.replication-factor:1}")
    private short replicationFactor;

    private final Environment environment;

    public KafkaConsumerConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Creates the main Kafka topic.
     * 
     * @return NewTopic instance
     */
    @Bean
    public NewTopic mainTopic() {
        return TopicBuilder.name(topicName)
                          .partitions(topicPartitions)
                          .replicas(replicationFactor)
                          .build();
    }

    /**
     * Creates the dead letter topic.
     * 
     * @return NewTopic instance
     */
    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name(topicName + ".DLT")
                          .partitions(topicPartitions)
                          .replicas(replicationFactor)
                          .build();
    }

    /**
     * Creates the main Kafka consumer factory with cloud-optimized settings.
     */
    @Bean
    public ConsumerFactory<String, ActionEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Core Kafka settings
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Deserialization configuration
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                 StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                 JsonDeserializer.class);
        
        // Cloud-optimized performance settings
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 
                 environment.getProperty("KAFKA_MAX_POLL_RECORDS", "500"));
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 
                 environment.getProperty("KAFKA_FETCH_MIN_BYTES", "1048576")); // 1MB
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 
                 environment.getProperty("KAFKA_FETCH_MAX_WAIT", "500"));
        
        // Container-aware settings
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 
                 environment.getProperty("KAFKA_SESSION_TIMEOUT", "30000"));
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 
                 environment.getProperty("KAFKA_HEARTBEAT_INTERVAL", "10000"));
        
        // AWS-specific security if enabled
        if (isSecurityEnabled()) {
            props.put("security.protocol", 
                     environment.getProperty("KAFKA_SECURITY_PROTOCOL", "SSL"));
            props.put("ssl.truststore.location", 
                     environment.getProperty("KAFKA_TRUSTSTORE_LOCATION"));
            props.put("ssl.keystore.location", 
                     environment.getProperty("KAFKA_KEYSTORE_LOCATION"));
            props.put("ssl.truststore.password", 
                     environment.getProperty("KAFKA_TRUSTSTORE_PASSWORD"));
            props.put("ssl.keystore.password", 
                     environment.getProperty("KAFKA_KEYSTORE_PASSWORD"));
        }

        JsonDeserializer<ActionEvent> deserializer = new JsonDeserializer<>(ActionEvent.class);
        deserializer.setRemoveTypeHeaders(false);
        deserializer.addTrustedPackages("*");
        
        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            deserializer
        );
    }

    /**
     * Creates the Kafka listener container factory optimized for cloud deployment.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ActionEvent> 
            kafkaListenerContainerFactory(KafkaTemplate<String, ActionEvent> kafkaTemplate) {
        
        ConcurrentKafkaListenerContainerFactory<String, ActionEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        
        // Basic configuration
        factory.setConsumerFactory(consumerFactory());
        
        // Container-optimized concurrency
        factory.setConcurrency(
            Integer.parseInt(environment.getProperty("KAFKA_CONSUMER_CONCURRENCY", "3"))
        );
        
        // Enable batch listening with cloud-optimized settings
        factory.setBatchListener(true);
        
        // Configure manual acknowledgment
        ContainerProperties props = factory.getContainerProperties();
        props.setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // DLT configuration
        DeadLetterPublishingRecoverer recoverer = 
            new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    // Send to topic-name.DLT
                    return record.topic() + ".DLT";
                });
        
        // Configure error handler with no retries and DLT
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(0L, 0L) // No retries
        );
        
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }

    private boolean isSecurityEnabled() {
        return "true".equalsIgnoreCase(
            environment.getProperty("KAFKA_SECURITY_ENABLED", "false")
        );
    }
}