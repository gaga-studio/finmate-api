ALTER TABLE finmate_demo_timeline_command
    ADD COLUMN request_expected_stage INTEGER,
    ADD COLUMN original_response TEXT;

UPDATE finmate_demo_timeline_command
SET request_expected_stage = stage - 1
WHERE request_expected_stage IS NULL;

UPDATE finmate_demo_timeline_command command
SET original_response = projection.body::text
FROM (
    SELECT command_row.id,
           jsonb_build_object(
               'fixtureId', command_row.fixture_id,
               'stage', command_row.stage,
               'mainGoal', jsonb_build_object(
                   'goalId', goal.id,
                   'title', goal.title,
                   'domain', goal.domain,
                   'currentAmountKrw', command_row.goal_amount_krw,
                   'targetAmountKrw', goal.target_amount_krw,
                   'targetMonth', to_char(goal.target_month, 'YYYY-MM'),
                   'state', goal.state,
                   'confirmedAt', goal.confirmed_at,
                   'calculationVersion', goal.calculation_version,
                   'dataState', 'FRESH',
                   'lastSyncedAt', command_row.synced_at
               ),
               'raid', jsonb_build_object(
                   'raidId', raid.id,
                   'goalId', goal.id,
                   'stage', command_row.raid_stage,
                   'bossHpBps', command_row.boss_hp_bps,
                   'progressBps', command_row.raid_progress_bps,
                   'financialStats', jsonb_build_object(
                       'spendingBps', command_row.spending_bps,
                       'savingBps', command_row.saving_bps,
                       'investmentJudgmentBps', command_row.investment_judgment_bps
                   ),
                   'xp', COALESCE(snapshot.xp, 0),
                   'coachCopyKey', command_row.coach_copy_key,
                   'calculationVersion', raid.calculation_version,
                   'dataState', 'FRESH',
                   'lastSyncedAt', command_row.synced_at
               ),
               'syntheticGroup', jsonb_build_object(
                   'groupId', 'group-demo-10',
                   'name', 'Demo adventurers',
                   'memberCount', 10,
                   'syntheticDemo', true,
                   'eligibleForProductionAggregation', false
               )
           ) AS body
    FROM finmate_demo_timeline_command command_row
    JOIN finmate_user_goal goal ON goal.user_id = command_row.user_id AND goal.state = 'ACTIVE'
    JOIN finmate_raid_projection raid ON raid.user_id = command_row.user_id AND raid.goal_id = goal.id
    LEFT JOIN LATERAL (
        SELECT xp
        FROM finmate_synthetic_financial_snapshot
        WHERE user_id = command_row.user_id
          AND goal_id = goal.id
          AND last_synced_at = command_row.synced_at
        ORDER BY id
        LIMIT 1
    ) snapshot ON true
    WHERE command_row.original_response IS NULL
) projection
WHERE command.id = projection.id;

ALTER TABLE finmate_demo_timeline_command
    ALTER COLUMN request_expected_stage SET NOT NULL,
    ALTER COLUMN original_response SET NOT NULL,
    ADD CONSTRAINT finmate_demo_timeline_command_expected_stage_check
        CHECK (request_expected_stage BETWEEN 0 AND 3);
