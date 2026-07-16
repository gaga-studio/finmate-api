# FinMate vNext verification record

## Current contract

- Verification date: 2026-07-16
- API baseline candidate: `codex/connect-synthetic-runtime`
- Web baseline candidate: `codex/connect-synthetic-runtime-ui`
- OpenAPI operations: 44
- OpenAPI schemas: 91
- Referenced JSON examples: 36
- Canonical entrypoint: `docs/vnext/README.md`
- Synthetic L1/L2 source: `gaga-studio/finmate-data` `v1.0.0`, commit `63ca3d046eba9ec510e377a28a0083233aefff61`
- Corrected L3 source: commit `eab7f87fce65345a22a072b04fd3de59d2a29513`
- Latest data-repository documentation reviewed: commit `b9af59c310ab52a98e0d3cef619263a900ec2a87`
- Source archive SHA-256: `278226514562ec13ddb69959622bc6342fbe0b2c45e1447fa77422e3f9d3dd58`
- L3 tree SHA-256: `872c3c7366028f7047957309a03607c31a1a033cb70c4249bd0183adc5182d2b`

Only files linked by the canonical entrypoint are current. Legacy material remains
non-normative and is excluded from product and implementation claims.

## Automated verification

| Command | Result |
| --- | --- |
| `./gradlew test` | `BUILD SUCCESSFUL`; 145 tests, 0 failures |
| `python3 -m unittest discover -s tools/finmate_data_import/tests -v` | 31 tests; `OK` |
| `.venv-import/bin/python tools/finmate_data_import/finmate_import.py verify-runtime ...` | locked provenance and all runtime counts matched `synthetic-runtime-v1` |
| `.venv-import/bin/python docs/vnext/06-api/verify_contracts.py` | `CONTRACT_VERIFICATION_OK operations=44 schemas=91 examples=36 structuralChecks=39 authChecks=14 goalChecks=5` |
| `.venv-import/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py -v` | 10 tests; `OK` |
| `finmate-web: npm run lint && npm run typecheck && npm run test && npm run build` | lint/typecheck/build passed; 40 tests, 0 failures |
| `finmate-web: npm run test:e2e` | 1 Mock API representative flow passed |
| `finmate-web: npm run test:e2e:api` | 4 actual API flows passed against an isolated PostgreSQL database, including 360/390/430px runtime checks |
| `finmate-web: npm run demo:record` | 1920×1080 H.264/AAC MP4, exactly 90.0 seconds, 2,215,189 bytes |

The checks cover:

- independent bundle/L3 provenance, exact 31-file L3 tree digest, stale/modified tree rejection;
- canonical mapping of locked Korean context values such as `정기`, `1인가구`,
  `고시원` and `쉐어하우스` into the runtime onboarding vocabulary;
- source-controlled SHA-256 for all 27 deterministic export files and post-export byte-tamper rejection;
- checksum, release metadata, table allowlists, field sanitization and normalized units;
- complete classification of 31 L3 tables into 16 runtime, 5 golden-only and 10 excluded;
- backend recalculation for income baseline, disposable income, spending, saving and investment inflow ratios;
- disclosure preview, explicit exact-value confirmation, required consent version and default privacy;
- field-level public profiles, required exact-value integrity and owner-linked immediate withdrawal;
- removal of withdrawn profiles from discovery, detail, routine and adaptation commands;
- quest XP and points idempotency, evidence-pending behavior and fixed cosmetic purchases;
- amount-free friend feed/status/streak reads with no social write contract;
- deterministic app-user-to-persona binding and self-exclusion from mate discovery;
- exact and ordered relaxed mate search with up to six eligible adventurers, stable tie-breaking,
  opaque public IDs and Korean display labels;
- product/holding/trade information separated from routine, quest, reward and raid effects;
- explore-before-goal, recommendation-first routine adaptation, read-only product information and record reconciliation.
- separate goal confirmation, character reports, quest acceptance, mate reports/search, six-frame demo progression and monthly journey runtime behavior;
- contract-synchronized web behavior against both Mock API and the actual API after the
  2026-07-14 design handoff;
- baseline diagnosis, quest detail, friend overview, comparison exploration, adventurer
  profile/report and information-only Hana product screens;
- 360px, 390px and 430px mobile visual checks with no console errors, failed image requests
  or horizontal overflow;
- deterministic 90-second demo generation with final goal amount `5,000,000 KRW`, H.264/AAC
  output SHA-256 `59fdc06aaddae1bdbbd17d4fb5a39f047c00fe141396ce522bbfad27630b0c58`.

## Locked-release verification

The downloaded release archive matched the locked SHA-256. The corrected 31-file L3 tree
matched commit `eab7f87` and the locked tree digest; the older L3 tree was rejected. A full
export of all 2,000 synthetic personas produced:

| Export | Rows |
| --- | ---: |
| Financial activities | 887,002 |
| Financial product holdings | 4,000 |
| Investment holdings | 8,912 |
| Investment trades | 16,576 |
| Runtime L3 records (16 tables) | 845,203 |
| Runtime feature profiles | 2,000 |
| Runtime behavior profiles | 2,000 |
| Runtime daily budgets | 388,000 |
| Runtime group profiles | 11 |
| Approved runtime routines | 3,939 |
| Synthetic friend relationships | 28,040 |
| Amount-free feed events | 10,000 |
| Streak projections | 27,660 |
| Golden comparison records (5 tables, not DB-loaded) | 444,000 |
| Deterministic cosmetic items | 3 |

The corrected `friend_group_stats` contained 2,000 groups: 1,832 unlocked and 168 locked.
All unlocked groups had all three averages, `1 <= scoredFriendCount <= friendCount`, and no
NaN or missing average survived the export. The 141 personas whose disposable income was not
positive retained undefined individual ratio-based stats; they were not converted to zero and
were excluded from group-score denominators.

The transformed export contained none of the scanned account-number, raw-memo,
source-reference, coupon, random-box or unsafe investment-quest tokens. Building seed
operations from the full export produced upserts for every runtime category and no golden
operation.

An isolated PostgreSQL 16.14 instance received migrations V1 through V17 and the complete
locked runtime export twice through the production loader. Both loads validated all 27 export
checksums and ended with the exact full-export counts shown above, including 887,002 financial
activities and 845,203 runtime L3 rows. The second load created no duplicates. A separate upgrade
test applied V1 through V11, inserted an obsolete runtime L3 row, and then verified that V12
removed it before recording corrected provenance; the following load restored only the approved
snapshot. The complete bootstrap also created all runtime projections listed above and no
exact-value public projection. Both databases stored the independent bundle commit, L3 commit
and L3 tree digest.

## Scope limits

These results validate the importer boundary, calculation code, disclosure/public-profile
projection, cosmetic rewards, read-only social API and the documented HTTP contract. They do
not claim:

- production or real-person MyData ingestion;
- production deployment or a load into a long-lived operating database;
- that imported private staging rows are automatically published;
- production deployment, monitoring or rollback rehearsal;
- written production rights for Paperlogy and the supplied character/background assets;
- results from the planned six-person formative usability test;
- runtime generative AI, investment execution or external rewards.

The five golden L3 tables remain regenerated comparison artifacts. They are not PostgreSQL
runtime seeds and may not be returned as product calculations.
