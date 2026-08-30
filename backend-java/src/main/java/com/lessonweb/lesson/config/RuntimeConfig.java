package com.lessonweb.lesson.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RuntimeConfig {

    @Bean
    public RestTemplate mineruRestTemplate(MineruProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.timeout().toMillis()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.timeout().toMillis()));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.setErrorHandler(new NoOpResponseErrorHandler());
        return restTemplate;
    }

    @Bean("documentTaskExecutor")
    public TaskExecutor documentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("document-parse-");
        executor.initialize();
        return executor;
    }
}
