package com.hexora.network;

import com.hexora.network.service.NetworkMonitorProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NetworkMonitorProperties.class)
public class NetworkMonitorConfiguration {

	@Bean(name = "taskScheduler")
	public ThreadPoolTaskScheduler networkTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(3);
		scheduler.setThreadNamePrefix("network-scheduler-");
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(30);
		return scheduler;
	}

	@Bean(name = "networkPortProbeExecutor")
	public ThreadPoolTaskExecutor networkPortProbeExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(32);
		executor.setMaxPoolSize(32);
		executor.setQueueCapacity(256);
		executor.setThreadNamePrefix("network-port-probe-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(35);
		return executor;
	}

	@Bean
	public MeterBinder networkPortProbeExecutorMetrics(
		@Qualifier("networkPortProbeExecutor") ThreadPoolTaskExecutor executor
	) {
		return registry -> {
			ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
			Gauge.builder("hexora.network.port.probe.active", pool, ThreadPoolExecutor::getActiveCount)
				.register(registry);
			Gauge.builder("hexora.network.port.probe.queued", pool, current -> current.getQueue().size())
				.register(registry);
			Gauge.builder("hexora.network.port.probe.completed", pool, ThreadPoolExecutor::getCompletedTaskCount)
				.register(registry);
		};
	}
}
