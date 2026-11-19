package org.unipod.unipoderpbackend.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebPushAsyncConfigurationTest {
    @Test
    void saturatedExecutorRunsRejectedTaskInline() throws InterruptedException {
        ThreadPoolTaskExecutor executor =
                (ThreadPoolTaskExecutor) new WebPushAsyncConfiguration().webPushTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();

        CountDownLatch runningTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseRunningTask = new CountDownLatch(1);
        CountDownLatch inlineTaskRan = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                runningTaskStarted.countDown();
                try {
                    releaseRunningTask.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(runningTaskStarted.await(5, TimeUnit.SECONDS));
            executor.execute(() -> { });

            executor.execute(inlineTaskRan::countDown);

            assertTrue(inlineTaskRan.await(1, TimeUnit.SECONDS));
        } finally {
            releaseRunningTask.countDown();
            executor.shutdown();
        }
    }
}
