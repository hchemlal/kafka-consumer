# Kafka Consumer with Spring Cache and Email Notifications

A Spring Boot application that demonstrates a robust Kafka consumer implementation with caching and email notifications for API failures.

## Features

- **Kafka Consumer**: Processes messages with individual acknowledgment
- **Spring Cache**: Uses Spring's caching abstraction with Caffeine
- **Email Notifications**: Beautiful HTML email alerts using FreeMarker templates
- **Error Handling**: Dead Letter Topic (DLT) for failed messages
- **Timestamp Validation**: Ensures messages are processed within 5 seconds
- **New York Timezone**: All timestamps are handled in NY timezone

## Prerequisites

- Java 17 or higher
- Maven
- Kafka broker running on localhost:9092
- SMTP server access (configured for Gmail by default)

## Configuration

1. Set up environment variables:
```bash
export SMTP_USERNAME=your-email@gmail.com
export SMTP_PASSWORD=your-app-specific-password
```

2. Update `application.properties`:
```properties
# Email settings
notification.email.from=your-alerts@domain.com
notification.email.to=your-admin@domain.com
```

## Building and Running

1. Build the project:
```bash
mvn clean install
```

2. Run the application:
```bash
mvn spring-boot:run
```

## Architecture

### Components

1. **KafkaMessageConsumer**: Main consumer implementation
   - Individual message acknowledgment
   - Timestamp validation
   - Error handling with DLT

2. **EmailService**: Handles email notifications
   - FreeMarker templating
   - HTML emails with responsive design
   - Detailed error reporting

3. **CacheConfig**: Spring cache configuration
   - Caffeine cache implementation
   - 24-hour expiry
   - 10,000 maximum entries

### Message Flow

1. Consumer receives message
2. Validates timestamp (< 5 seconds old)
3. Checks cache for duplicates
4. Calls external API
5. If API fails:
   - Sends HTML email alert
   - Forwards to DLT
6. If successful:
   - Updates cache
   - Acknowledges message

## Email Template

The email alerts include:
- Error details
- Record information (topic, partition, offset)
- Stack trace
- Timestamps in NY timezone
- Responsive design for mobile devices

## Contributing

Feel free to submit issues and enhancement requests!
