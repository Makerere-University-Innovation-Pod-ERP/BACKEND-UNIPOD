package org.unipod.unipoderpbackend.config;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class WebPushAsyncConfiguration {
    private static final Logger log = LoggerFactory.getLogger(WebPushAsyncConfiguration.class);

    @Bean("webPushTaskExecutor")
    Executor webPushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("web-push-");
        executor.setRejectedExecutionHandler((task, threadPool) -> {
            if (threadPool.isShutdown()) {
                log.error("Web push executor is shut down; running the rejected push task inline.");
            } else {
                log.warn("Web push executor is saturated; running the rejected push task inline.");
            }
            task.run();
        });
        return executor;
    }
}
