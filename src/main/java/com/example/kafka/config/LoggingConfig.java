package com.example.kafka.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.http.HttpServletRequest;

@Configuration
public class LoggingConfig {
    
    private static final Logger log = LoggerFactory.getLogger(LoggingConfig.class);

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter() {
            @Override
            protected void beforeRequest(HttpServletRequest request, String message) {
                log.info("Incoming Request: {} {}", request.getMethod(), request.getRequestURI());
            }

            @Override
            protected void afterRequest(HttpServletRequest request, String message) {
                log.info("Completed Request: {} {}", request.getMethod(), request.getRequestURI());
            }
        };
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(10000);
        filter.setIncludeHeaders(true);
        return filter;
    }
}
