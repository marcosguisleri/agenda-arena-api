package br.dev.guisleri.agendaarena.match;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V18: partidas existentes recebem a cópia dos dados da reserva, e o banco garante uma única
 * origem. Usa o Flyway direto (sem Spring) para parar na V17, inserir dados e então aplicar a V18.
 */
@Testcontainers
class MatchSourceMigrationTests {

    private static final LocalDate DATE = LocalDate.of(2030, 1, 7);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        flyway(dataSource, null).clean();
        flyway(dataSource, "17").migrate();
    }

    @Test
    void shouldCopyReservationDataIntoExistingMatches() {
        long organizerId = insertUser("organizer@example.com");
        long slotId = insertSlot(new BigDecimal("150.00"));
        long reservationId = insertReservation(organizerId, slotId);
        jdbc.update("""
                INSERT INTO matches (reservation_id, public_token, status, created_at)
                VALUES (?, ?, 'OPEN', now())
                """, reservationId, "T".repeat(43));

        migrateToLatest();

        Map<String, Object> match = jdbc.queryForMap(
                "SELECT * FROM matches WHERE reservation_id = ?", reservationId);

        assertThat(((Number) match.get("organizer_id")).longValue()).isEqualTo(organizerId);
        assertThat(((Number) match.get("schedule_slot_id")).longValue()).isEqualTo(slotId);
        assertThat(((Date) match.get("match_date")).toLocalDate()).isEqualTo(DATE);
        assertThat(((Time) match.get("start_time")).toLocalTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(((Time) match.get("end_time")).toLocalTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat((BigDecimal) match.get("price")).isEqualByComparingTo("150.00");
        assertThat(match.get("recurring_reservation_id")).isNull();
        assertThat(match.get("occurrence_date")).isNull();
    }

    @Test
    void shouldRequireExactlyOneSource() {
        migrateToLatest();
        long organizerId = insertUser("organizer@example.com");
        long slotId = insertSlot(new BigDecimal("150.00"));
        long reservationId = insertReservation(organizerId, slotId);
        long recurringId = insertRecurring(slotId);

        assertRejected(organizerId, slotId, null, null, null, "chk_matches_single_source");
        assertRejected(organizerId, slotId, reservationId, recurringId, DATE,
                "chk_matches_single_source");
        assertRejected(organizerId, slotId, null, recurringId, null, "chk_matches_single_source");
        assertRejected(organizerId, slotId, null, recurringId, DATE.plusDays(7),
                "chk_matches_occurrence_date_consistent");

        insertMatch(organizerId, slotId, null, recurringId, DATE, "A");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matches", Long.class)).isEqualTo(1);
    }

    @Test
    void shouldAllowOneMatchPerOccurrence() {
        migrateToLatest();
        long organizerId = insertUser("organizer@example.com");
        long slotId = insertSlot(new BigDecimal("150.00"));
        long recurringId = insertRecurring(slotId);
        insertMatch(organizerId, slotId, null, recurringId, DATE, "A");

        assertThatThrownBy(() -> insertMatch(organizerId, slotId, null, recurringId, DATE, "B"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_matches_recurring_occurrence");
    }

    // ---------- Helpers ----------

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false);

        if (target != null) {
            configuration.target(target);
        }

        return configuration.load();
    }

    private void migrateToLatest() {
        flyway((DriverManagerDataSource) jdbc.getDataSource(), null).migrate();
    }

    private void assertRejected(
            long organizerId,
            long slotId,
            Long reservationId,
            Long recurringId,
            LocalDate occurrenceDate,
            String constraint
    ) {
        assertThatThrownBy(() ->
                insertMatch(organizerId, slotId, reservationId, recurringId, occurrenceDate, "X"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private void insertMatch(
            long organizerId,
            long slotId,
            Long reservationId,
            Long recurringId,
            LocalDate occurrenceDate,
            String tokenChar
    ) {
        jdbc.update("""
                INSERT INTO matches (reservation_id, recurring_reservation_id, occurrence_date,
                    organizer_id, schedule_slot_id, match_date, start_time, end_time, price,
                    public_token, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, '19:00', '20:00', 150.00, ?, 'OPEN', now())
                """, reservationId, recurringId, occurrenceDate, organizerId, slotId, DATE,
                tokenChar.repeat(43));
    }

    private long insertUser(String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (name, email, phone, password_hash)
                VALUES ('Organizador', ?, '11999991234', 'hash') RETURNING id
                """, Long.class, email);
    }

    private long insertSlot(BigDecimal price) {
        long establishmentId = jdbc.queryForObject(
                "INSERT INTO establishments (name) VALUES ('Arena') RETURNING id", Long.class);
        long sportFieldId = jdbc.queryForObject("""
                INSERT INTO sports_fields (establishment_id, name, sport_type)
                VALUES (?, 'Campo 1', 'SOCIETY') RETURNING id
                """, Long.class, establishmentId);

        return jdbc.queryForObject("""
                INSERT INTO schedule_slots (sport_field_id, day_of_week, start_time, price)
                VALUES (?, 'MONDAY', '19:00', ?) RETURNING id
                """, Long.class, sportFieldId, price);
    }

    private long insertReservation(long organizerId, long slotId) {
        return jdbc.queryForObject("""
                INSERT INTO reservations (organizer_id, schedule_slot_id, reservation_date,
                    start_time, end_time, price, status, origin)
                VALUES (?, ?, ?, '19:00', '20:00', 150.00, 'CONFIRMED', 'ONLINE') RETURNING id
                """, Long.class, organizerId, slotId, DATE);
    }

    private long insertRecurring(long slotId) {
        return jdbc.queryForObject("""
                INSERT INTO recurring_reservations (schedule_slot_id, responsible_name,
                    responsible_phone, start_date)
                VALUES (?, 'Responsavel', '11999991234', ?) RETURNING id
                """, Long.class, slotId, DATE);
    }
}
