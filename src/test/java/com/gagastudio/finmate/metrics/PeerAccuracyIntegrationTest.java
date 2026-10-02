package com.gagastudio.finmate.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gagastudio.finmate.ledger.LedgerImporter;
import com.gagastudio.finmate.support.PostgresIntegrationTest;

@SpringBootTest
class PeerAccuracyIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate JULY = LocalDate.of(2026, 7, 1);
    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerImporter importer;
    @Autowired MonthlyRollup rollup;
    @Autowired PeerCompareService peers;
    private UUID spender;

    @BeforeEach
    void twoLoadedPeopleOnlyOneSpends() {
        jdbc.execute("TRUNCATE persona CASCADE");
        importer.importFrom(Path.of("demo", "bundles"), 2);
        jdbc.update("DELETE FROM ledger_entry");
        spender = jdbc.queryForObject("SELECT id FROM persona WHERE external_id = 'P0001'", UUID.class);
        jdbc.update("""
            INSERT INTO ledger_entry(persona_id, external_id, occurred_at, merchant, amount,
                category, flow, major, minor)
            VALUES (?, 'one', '2026-07-10 12:00:00', 'fixture', -100, 'food', '소비', '식비', '외식')
            """, spender);
        rollup.rebuildAll();
    }

    @Test
    void loadedNonSpenderIsZeroInTheSameDenominator() {
        var comparison = peers.forPersona(spender, JULY);
        assertThat(comparison.peerCount()).isEqualTo(2);
        assertThat(comparison.mySpend()).isEqualTo(100);
        assertThat(comparison.peerAvgSpend()).isEqualTo(50);
        assertThat(comparison.bands()).singleElement().satisfies(group -> {
            assertThat(group.members()).isEqualTo(2);
            assertThat(group.avgSpend()).isEqualTo(50);
        });
    }

    @Test
    void queryAlternativesMatchTheHandCalculatedAverageIncludingZero() {
        for (String sql : java.util.List.of(PeerQueryAlternatives.DISTINCT,
                PeerQueryAlternatives.REWRITTEN, PeerQueryAlternatives.ROLLUP)) {
            var result = jdbc.queryForMap(sql, JULY, JULY.plusMonths(1).minusDays(1));
            assertThat(result.get("members")).isEqualTo(2L);
            assertThat(result.get("avg_spend")).isEqualTo(50L);
        }
    }

    @Test
    void unloadedMonthHasNoComparisonPopulation() {
        assertThat(peers.byIncomeBand(JULY.plusMonths(1))).isEmpty();
    }

    @Test
    void rebuildRemovesAggregateAfterLastLedgerEntryIsDeleted() {
        jdbc.update("DELETE FROM ledger_entry WHERE persona_id = ?", spender);
        rollup.rebuildAll();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persona_month", Long.class)).isZero();
        assertThat(peers.forPersona(spender, JULY).peerAvgSpend()).isZero();
    }

    @Test
    void partiallyCoveredMonthIsExcludedFromCohort() {
        jdbc.update("UPDATE persona SET data_from = '2026-07-15' WHERE external_id = 'P0002'");
        var comparison = peers.forPersona(spender, JULY);
        assertThat(comparison.peerCount()).isEqualTo(1);
        assertThat(comparison.peerAvgSpend()).isEqualTo(100);
    }
}
