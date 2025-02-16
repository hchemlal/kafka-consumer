package com.example.kafka;


import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import javax.mail.internet.MimeMessage;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfig;
    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    
    @Value("${notification.email.from}")
    private String fromEmail;
    
    @Value("${notification.email.to}")
    private String toEmail;
    
    public EmailService(JavaMailSender mailSender, Configuration freemarkerConfig) {
        this.mailSender = mailSender;
        this.freemarkerConfig = freemarkerConfig;
    }
    
    public void sendApiFailureAlert(String errorMessage, ConsumerRecord<String, ActionEvent> record, Exception exception) {
        try {
            // Prepare template data
            Map<String, Object> model = new HashMap<>();
            model.put("errorMessage", errorMessage);
            
            Map<String, Object> recordDetails = new HashMap<>();
            recordDetails.put("topic", record.topic());
            recordDetails.put("partition", record.partition());
            recordDetails.put("offset", record.offset());
            recordDetails.put("key", record.key());
            recordDetails.put("timestamp", ZonedDateTime.parse(record.value().getTimestamp())
                                                      .withZoneSameInstant(NY_ZONE)
                                                      .format(TIMESTAMP_FORMATTER));
            model.put("record", recordDetails);
            
            // Format stack trace
            StringBuilder stackTrace = new StringBuilder();
            for (StackTraceElement element : exception.getStackTrace()) {
                stackTrace.append(element.toString()).append("\n");
            }
            model.put("stackTrace", stackTrace.toString());
            
            // Add alert generation time
            model.put("alertTime", ZonedDateTime.now(NY_ZONE).format(TIMESTAMP_FORMATTER));
            
            // Process template
            Template template = freemarkerConfig.getTemplate("api-failure-alert.ftlh");
            String htmlContent = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            
            // Create email message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("API Failure Alert - Kafka Consumer");
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Sent API failure alert email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send API failure alert email", e);
        }
    }
}
