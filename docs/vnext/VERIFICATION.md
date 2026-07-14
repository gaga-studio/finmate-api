# FinMate vNext verification record

## Current contract

- Verification date: 2026-07-14
- API baseline candidate: `main@3892c8b179700f3f0f3dff7a76875147b5256edc`
- Web baseline: `main@dfb2f3104045952c390f013e711335f7066b1d29`
- OpenAPI operations: 44
- OpenAPI schemas: 86
- Referenced JSON examples: 36
- Canonical entrypoint: `docs/vnext/README.md`
- Synthetic L1/L2 source: `gaga-studio/finmate-data` `v1.0.0`, commit `63ca3d046eba9ec510e377a28a0083233aefff61`
- Corrected L3 source: commit `22243bce34131737fc762675f0817ead08bc165a`
- Source archive SHA-256: `278226514562ec13ddb69959622bc6342fbe0b2c45e1447fa77422e3f9d3dd58`
- L3 tree SHA-256: `dd30ae91f5e517f2a502ce46b2dfe3666107562e2dc52edb25fec48b893c3c33`

Only files linked by the canonical entrypoint are current. Legacy material remains
non-normative and is excluded from product and implementation claims.

## Automated verification

| Command | Result |
| --- | --- |
| `./gradlew test` | `BUILD SUCCESSFUL`; 118 tests, 0 failures |
| `python3 -m unittest discover -s tools/finmate_data_import/tests -v` | 22 tests; `OK` |
| `.venv/bin/python docs/vnext/06-api/verify_contracts.py` | `CONTRACT_VERIFICATION_OK operations=44 schemas=86 examples=36 structuralChecks=39 authChecks=14 goalChecks=5` |
| `.venv/bin/python -m unittest docs/vnext/06-api/test_build_mock_spec.py -v` | 10 tests; `OK` |
| `finmate-web: npm run lint && npm run typecheck && npm run test && npm run build` | lint/typecheck/build passed; 33 tests, 0 failures |
| `finmate-web: npm run test:e2e` | 1 Mock API representative flow passed |
| `finmate-web: npm run test:e2e:api` | 3 actual API flows passed against an isolated PostgreSQL database |
| `finmate-web: npm run demo:record` | 1920×1080 H.264/AAC MP4, exactly 90.0 seconds, 2,215,189 bytes |

The checks cover:

- independent bundle/L3 provenance, exact 31-file L3 tree digest, stale/modified tree rejection;
- source-controlled SHA-256 for all 27 deterministic export files and post-export byte-tamper rejection;
- checksum, release metadata, table allowlists, field sanitization and normalized units;
- complete classification of 31 L3 tables into 16 runtime, 5 golden-only and 10 excluded;
- backend recalculation for income baseline, disposable income, spending, saving and investment inflow ratios;
- disclosure preview, explicit exact-value confirmation, required consent version and default privacy;
- field-level public profiles, required exact-value integrity and owner-linked immediate withdrawal;
- removal of withdrawn profiles from discovery, detail, routine and adaptation commands;
- quest XP and points idempotency, evidence-pending behavior and fixed cosmetic purchases;
- amount-free friend feed/status/streak reads with no social write contract;
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
matched commit `22243bc` and the locked tree digest; the older L3 tree was rejected. A full
export of all 2,000 synthetic personas produced:

| Export | Rows |
| --- | ---: |
| Financial activities | 887,002 |
| Financial product holdings | 4,000 |
| Investment holdings | 8,912 |
| Investment trades | 16,576 |
| Runtime L3 records (16 tables) | 845,202 |
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

An isolated PostgreSQL 16.14 instance received migrations V1 through V12 and the complete
locked runtime export twice through the production loader. Both loads validated all 27 export
checksums and ended with the exact full-export counts shown above, including 887,002 financial
activities and 845,202 runtime L3 rows. The second load created no duplicates. A separate upgrade
test applied V1 through V11, inserted an obsolete runtime L3 row, and then verified that V12
removed it before recording corrected provenance; the following load restored only the approved
snapshot. Both databases stored the independent bundle commit, L3 commit and L3 tree digest.

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
