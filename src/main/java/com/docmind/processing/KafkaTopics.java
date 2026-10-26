package com.docmind.processing;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String DOCUMENT_UPLOADED = "docmind.document-uploaded";
    public static final String DOCUMENT_UPLOADED_DLT = DOCUMENT_UPLOADED + ".dlt";

    @Bean
    NewTopic documentUploadedTopic() {
        return TopicBuilder.name(DOCUMENT_UPLOADED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic documentUploadedDeadLetterTopic() {
        return TopicBuilder.name(DOCUMENT_UPLOADED_DLT).partitions(1).replicas(1).build();
    }
}
