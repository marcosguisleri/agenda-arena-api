package br.dev.guisleri.agendaarena.match;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V19: a FK {@code matches.schedule_slot_id} passa a ter índice. Usa o Flyway direto (sem Spring),
 * como os demais testes de migration.
 */
@Testcontainers
class MatchScheduleSlotIndexMigrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void shouldIndexTheScheduleSlotForeignKeyOfMatches() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        List<String> definitions = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes "
                        + "WHERE tablename = 'matches' AND indexname = 'idx_matches_schedule_slot'",
                String.class
        );

        assertThat(definitions).hasSize(1);
        assertThat(definitions.getFirst()).contains("(schedule_slot_id)");
    }
}
