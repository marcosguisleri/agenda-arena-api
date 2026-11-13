package br.dev.guisleri.agendaarena.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Fonte de aleatoriedade injetável (como o {@code Clock}), para os testes poderem fixar a semente.
 * {@link SecureRandom} é seguro para uso concorrente.
 */
@Configuration
public class RandomConfig {

    @Bean
    public RandomGenerator randomGenerator() {
        return new SecureRandom();
    }

}
