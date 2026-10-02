package com.gagastudio.finmate.ledger;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.gagastudio.finmate.metrics.OverviewService;
import com.gagastudio.finmate.metrics.PeriodType;

@Service
public class TransactionService {
    private final JdbcTemplate jdbc;
    private final OverviewService overviews;

    public TransactionService(JdbcTemplate jdbc, OverviewService overviews) {
        this.jdbc = jdbc;
        this.overviews = overviews;
    }

    public record Entry(long id, LocalDate date, String merchant, long amount, String category, String flow) {}
    public record Page(List<Entry> items, long total, int page, int size, String dataStatus, String source) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page forPersona(UUID personaId, PeriodType period, LocalDate date, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page는 0 이상, size는 1~100이어야 합니다");
        }
        var overview = overviews.of(personaId, period, date);
        if (overview.dataStatus().equals("NO_DATA")) {
            return new Page(List.of(), 0, page, size, "NO_DATA", "SYNTHETIC");
        }
        Long total = jdbc.queryForObject("""
            SELECT count(*) FROM ledger_entry WHERE persona_id = ? AND occurred_on BETWEEN ? AND ?
            """, Long.class, personaId, overview.start(), overview.end());
        List<Entry> items = jdbc.query("""
            SELECT id, occurred_on, merchant, amount, category, flow FROM ledger_entry
            WHERE persona_id = ? AND occurred_on BETWEEN ? AND ?
            ORDER BY occurred_at DESC, id DESC LIMIT ? OFFSET ?
            """, (rs, i) -> new Entry(rs.getLong("id"), rs.getObject("occurred_on", LocalDate.class),
                rs.getString("merchant"), rs.getLong("amount"), rs.getString("category"), rs.getString("flow")),
            personaId, overview.start(), overview.end(), size, (long) page * size);
        return new Page(items, total, page, size, "AVAILABLE", "SYNTHETIC");
    }
}
