package com.example.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import java.util.Collection;

public class KafkaRebalanceListener implements ConsumerRebalanceListener {

    private final Consumer<?, ?> consumer;

    public KafkaRebalanceListener(Consumer<?, ?> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        // Logic to handle partition revocation
        System.out.println("Partitions revoked: " + partitions);
        // Commit offsets or handle state as necessary
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        // Logic to handle partition assignment
        System.out.println("Partitions assigned: " + partitions);
        // Seek to the last committed offsets or start from the beginning
    }
}
