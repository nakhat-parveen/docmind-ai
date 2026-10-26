package com.docmind.processing;

import com.docmind.ingestion.DocumentProcessingException;
import com.docmind.ingestion.UnsupportedDocumentTypeException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Failed messages are retried a few times with a pause in between, because errors
 * like an unreachable embedding model are often temporary. Errors that cannot go
 * away by retrying, such as a corrupt PDF, skip the retries. When a message gives
 * up, the document is marked as failed and the message is parked on the
 * dead-letter topic so it can be inspected or replayed later.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate,
                                          DocumentProcessor processor,
                                          ProcessingProperties properties,
                                          ProcessingMetrics metrics) {
        var deadLetter = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(KafkaTopics.DOCUMENT_UPLOADED_DLT, -1));

        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            if (record.value() instanceof DocumentUploadedEvent event) {
                processor.markFailed(event.documentId(), cause(exception).getMessage());
            }
            deadLetter.accept(record, exception);
        };

        var backOff = new FixedBackOff(properties.retryInterval().toMillis(), properties.maxAttempts() - 1L);
        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(DocumentProcessingException.class, UnsupportedDocumentTypeException.class);
        errorHandler.setRetryListeners((record, exception, attempt) -> metrics.listenerError(cause(exception)));
        return errorHandler;
    }

    private static Throwable cause(Exception exception) {
        return exception instanceof ListenerExecutionFailedException && exception.getCause() != null
                ? exception.getCause()
                : exception;
    }
}
