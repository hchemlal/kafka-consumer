package com.example.kafka.config;

import com.example.kafka.ActionEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.support.LogIfLevelEnabled;
import org.springframework.kafka.support.serializer.JsonDeserializer;

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

        /*Starting with version 2.1.1, you can now set the client.id property for consumers created by the annotation
        @KafkaListener Annotation. The clientIdPrefix is suffixed with -n, where n is an integer representing the container number when using concurrency */
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "downstream-spire-reader");
        
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
            kafkaManualAckListenerContainerFactory(KafkaTemplate<String, ActionEvent> kafkaTemplate) {
        
        ConcurrentKafkaListenerContainerFactory<String, ActionEvent> containerFactory =
            new ConcurrentKafkaListenerContainerFactory<>();
        
        // Basic configuration
        containerFactory.setConsumerFactory(consumerFactory());
        
        // Container-optimized concurrency
        containerFactory.setConcurrency(
            Integer.parseInt(environment.getProperty("KAFKA_CONSUMER_CONCURRENCY", "1"))
        );
        
        // Enable batch listening with cloud-optimized settings
        //@KafkaListener methods to receive the entire batch of consumer records received from the consumer poll
        //Non-Blocking Retries are not supported with batch listeners.
        containerFactory.setBatchListener(true);
        
        // Configure manual acknowledgment
        ContainerProperties containerProps = containerFactory.getContainerProperties();

        /*Because the listener container has its own mechanism for committing offsets,
        it prefers the Kafka ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to be false.
        Starting with version 2.3, it unconditionally sets it to false unless specifically set in the consumer factory
        or the container’s consumer property overrides.
         */

        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL);

        /* When 'true' and 'INFO' logging is enabled each listener container writes a log message
        summarizing its configuration properties */
        containerProps.setLogContainerConfig(true);

        /* By default, logging of topic offset commits is performed at the DEBUG logging level, we want to see it at
        INFO level.
         */
        containerProps.setCommitLogLevel(LogIfLevelEnabled.Level.INFO);

        /* Starting with version 2.2, a new container property called missingTopicsFatal has been added (default: false since 2.3.4).
        This prevents the container from starting if any of the configured topics are not present on the broker.
        It does not apply if the container is configured to listen to a topic pattern (regex).
        Previously, the container threads looped within the consumer.poll() method waiting for the topic to appear
        while logging many messages. Aside from the logs, there was no indication that there was a problem
        */
        containerProps.setMissingTopicsFatal(true);

        containerProps.setMessageListener(new MessageListener<Integer, String>() {

            /* Invoked with data from kafka.*/
            @Override
            public void onMessage(ConsumerRecord<Integer, String> data) {

            }
        });

        // DLT configuration
        DeadLetterPublishingRecoverer recoverer = 
            new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    // Send to topic-name.DLT
                    return null; //record.topic() + ".DLT";
                });
        
//        // Configure error handler with no retries and DLT
//        ErrorHandler errorHandler = new ConsumerAwareErrorHandler(
//            recoverer,
//            new FixedBackOff(0L, 0L) // No retries
//        );
//
//        factory.setCommonErrorHandler(errorHandler);

        return containerFactory;
    }

    private boolean isSecurityEnabled() {
        return "true".equalsIgnoreCase(
            environment.getProperty("KAFKA_SECURITY_ENABLED", "false")
        );
    }
}