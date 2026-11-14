package br.dev.guisleri.agendaarena.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AppMailProperties.class)
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    /**
     * Executor só dos e-mails: poucas threads e fila limitada. Se a fila encher, o e-mail é
     * descartado com um aviso no log em vez de lançar exceção: o envio roda logo depois do commit
     * e uma exceção ali viraria erro HTTP de uma reserva que já foi gravada.
     */
    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor(AppMailProperties properties) {
        if (!properties.async()) {
            return Runnable::run;
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("email-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("E-mail queue is full; a notification was dropped"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);

        return executor;
    }
}
