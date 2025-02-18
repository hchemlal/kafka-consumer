package com.example.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.junit.jupiter.api.Test;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class KafkaPerformanceTest {

    private final String topic = "your_topic_name"; // Replace with your topic

    @Test
    public void testKafkaProducerPerformance() throws InterruptedException, ExecutionException {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092"); // Replace with your broker address
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        int messageCount = 10000; // Number of messages to send
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < messageCount; i++) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, "key" + i, "value" + i);
            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                    }
                }
            });
        }

        producer.close();
        long endTime = System.currentTimeMillis();
        System.out.println("Sent " + messageCount + " messages in " + (endTime - startTime) + " ms");
    }
}
