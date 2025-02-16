package com.example.kafka;

import com.example.kafka.CustomKafkaProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageProducerController {

    @Autowired
    private CustomKafkaProducer kafkaProducer;

    @PostMapping("/send")
    public String sendMessage(@RequestBody String message) {
        String topic = "action-events"; // Replace with your topic name
        kafkaProducer.sendMessage(topic, null, message); // Sending message with null key
        return "Message sent to topic: " + topic;
    }
}
