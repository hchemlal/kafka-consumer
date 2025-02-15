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

    @Bean
    public NewTopic mainTopic() {
        return TopicBuilder.name(topicName)
                          .partitions(topicPartitions)
                          .replicas(replicationFactor)
                          .build();
    }

    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name(topicName + ".DLT")
                          .partitions(topicPartitions)
                          .replicas(replicationFactor)
                          .build();
    }

    @Bean
    public ConsumerFactory<String, ActionEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.kafka");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ActionEvent.class.getName());
        
        // Disable auto-commit since we're doing manual commits
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Poll multiple records
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        
        // Set fetch size to minimize network overhead
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100);
        
        // Enable exactly-once processing
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            new JsonDeserializer<>(ActionEvent.class, false)
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ActionEvent> kafkaListenerContainerFactory(
            KafkaTemplate<String, ActionEvent> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, ActionEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Configure manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Set concurrency to 1 to ensure sequential processing
        factory.setConcurrency(1);
        
        // Enable batch mode to receive multiple records
        factory.setBatchListener(true);
        
        // Configure DLT handling
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
}