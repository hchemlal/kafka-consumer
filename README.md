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

## Environment Variables

### Required Variables
- `KAFKA_BROKERS` - Kafka broker addresses (e.g., `broker1:9092,broker2:9092`)
- `KAFKA_CONSUMER_GROUP` - Consumer group ID (e.g., `my-group`)

### Optional Variables with Defaults

#### Kafka Settings
- `KAFKA_CONSUMER_CONCURRENCY` (default: 1) - Number of concurrent consumers
- `KAFKA_MAX_POLL_RECORDS` (default: 500) - Maximum records per poll
- `KAFKA_FETCH_MIN_BYTES` (default: 1048576) - Minimum bytes to fetch
- `KAFKA_FETCH_MAX_WAIT` (default: 500) - Maximum wait time for fetch

#### Security Settings
- `KAFKA_SECURITY_ENABLED` (default: false) - Enable SSL/TLS security
- `KAFKA_SECURITY_PROTOCOL` (default: PLAINTEXT) - Security protocol (PLAINTEXT/SSL)
- `KAFKA_KEYSTORE_LOCATION` - Path to SSL keystore
- `KAFKA_TRUSTSTORE_LOCATION` - Path to SSL truststore
- `KAFKA_KEYSTORE_PASSWORD` - Keystore password
- `KAFKA_TRUSTSTORE_PASSWORD` - Truststore password

#### AWS Settings
- `AWS_REGION` (default: us-east-1) - AWS region
- `CLOUDWATCH_ENABLED` (default: false) - Enable CloudWatch metrics
- `CLOUDWATCH_NAMESPACE` (default: KafkaConsumer) - CloudWatch namespace
- `CLOUDWATCH_BATCH_SIZE` (default: 20) - Metrics batch size

## Deployment Options

### Local Development
```bash
# Set environment variables in .env file or export directly
export KAFKA_BROKERS=localhost:9092
export KAFKA_CONSUMER_GROUP=my-group
```

### Docker Compose
```bash
# All variables are set in docker-compose.yml
docker-compose up
```

### AWS EC2
```bash
# Set environment variables in EC2 User Data or through AWS Systems Manager
aws ssm put-parameter \
    --name "/kafka-consumer/prod/KAFKA_BROKERS" \
    --value "broker1:9092,broker2:9092" \
    --type "SecureString"
```

### Container Settings
```bash
# Run with environment variables
docker run -d \
    -e KAFKA_BROKERS=broker1:9092,broker2:9092 \
    -e KAFKA_CONSUMER_GROUP=my-group \
    -e KAFKA_SECURITY_ENABLED=true \
    -v /secrets:/secrets \
    -p 8080:8080 \
    -p 8081:8081 \
    kafka-consumer:latest
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
