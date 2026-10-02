package com.gagastudio.finmate.metrics;

/** 실험 전용. 모든 대안의 모집단·무거래자·반올림 정의는 같다. */
final class PeerQueryAlternatives {
    private PeerQueryAlternatives() {}
    private static final String COHORT = """
        WITH bounds AS (SELECT ?::date AS first, ?::date AS last), eligible AS (
          SELECT p.* FROM persona p, bounds b WHERE p.data_from <= b.first AND p.data_to >= b.last
        )
        """;
    static final String DISTINCT = COHORT + """
        SELECT p.income_band, count(DISTINCT p.id)::bigint AS members,
          round(COALESCE(-sum(e.amount) FILTER (WHERE e.flow = '소비'), 0)::numeric
            / count(DISTINCT p.id))::bigint AS avg_spend
        FROM eligible p CROSS JOIN bounds b
        LEFT JOIN ledger_entry e ON e.persona_id = p.id AND e.occurred_on BETWEEN b.first AND b.last
        GROUP BY p.income_band ORDER BY p.income_band
        """;
    static final String REWRITTEN = COHORT + """
        , spent AS (
          SELECT e.persona_id, -sum(e.amount) AS spend FROM ledger_entry e CROSS JOIN bounds b
          WHERE e.flow = '소비' AND e.occurred_on BETWEEN b.first AND b.last GROUP BY e.persona_id
        )
        SELECT p.income_band, count(*)::bigint AS members,
          round(avg(COALESCE(s.spend, 0)))::bigint AS avg_spend
        FROM eligible p LEFT JOIN spent s ON s.persona_id = p.id
        GROUP BY p.income_band ORDER BY p.income_band
        """;
    static final String ROLLUP = COHORT + """
        SELECT p.income_band, count(*)::bigint AS members,
          round(avg(COALESCE(m.spend, 0)))::bigint AS avg_spend
        FROM eligible p CROSS JOIN bounds b
        LEFT JOIN persona_month m ON m.persona_id = p.id AND m.month = b.first
        GROUP BY p.income_band ORDER BY p.income_band
        """;
}
