package com.krdevops.springai.chat.config;

import com.krdevops.springai.config.OperationalResilienceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class EgovAsyncConfig implements WebMvcConfigurer {

    private final OperationalResilienceProperties properties;

    public EgovAsyncConfig(OperationalResilienceProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "documentProcessingExecutor")
    public Executor documentProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int concurrency = properties.getBulkhead().getIndexingConcurrency();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(concurrency * 25);
        executor.setThreadNamePrefix("doc-processor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "captureExecutor")
    public Executor captureExecutor() {
        return boundedExecutor("capture-", properties.getBulkhead().getCaptureConcurrency(), 8);
    }

    @Bean(name = "chatExecutor")
    public Executor chatExecutor() {
        return boundedExecutor("chat-", properties.getBulkhead().getChatConcurrency(), 16);
    }

    @Bean(name = "visionExecutor")
    public Executor visionExecutor() {
        return boundedExecutor("vision-", properties.getBulkhead().getCaptureConcurrency(), 4);
    }

    @Bean(name = "mvcAsyncExecutor")
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mvc-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
        configurer.setDefaultTimeout(properties.getTimeout().getSseTotal().toMillis());
    }

    private ThreadPoolTaskExecutor boundedExecutor(String prefix, int concurrency, int queueMultiplier) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(concurrency * queueMultiplier);
        executor.setThreadNamePrefix(prefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
