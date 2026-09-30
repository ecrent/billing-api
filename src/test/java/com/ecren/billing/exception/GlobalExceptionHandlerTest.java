package com.ecren.billing.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void handleGeneric_returnsGenericProblemDetailAndLogsException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ProblemDetail problem = handler.handleGeneric(new RuntimeException("boom"));

            assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).isEqualTo("Unhandled exception");
                assertThat(event.getThrowableProxy().getClassName())
                        .isEqualTo(RuntimeException.class.getName());
            });
        } finally {
            logger.detachAppender(appender);
        }
    }
}
