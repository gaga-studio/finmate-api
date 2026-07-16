# FinMate synthetic-data importer

This tool is the product-side trust boundary for `gaga-studio/finmate-data` `v1.0.0`.
It verifies the release archive, exports only approved fields, and idempotently upserts the
transformed seed into PostgreSQL. The 937 MB unpacked source bundle is never committed here.

The immutable L1/L2 bundle and the independently corrected L3 tree have separate provenance:

- bundle commit: `63ca3d046eba9ec510e377a28a0083233aefff61`
- L3 commit: `eab7f87fce65345a22a072b04fd3de59d2a29513`
- L3 tree SHA-256: `872c3c7366028f7047957309a03607c31a1a033cb70c4249bd0183adc5182d2b`

## Safety boundary

- Raw account numbers, transaction memos, API references, detailed employers, and detailed locations are discarded.
- Product names, holdings, and trades are imported into private staging tables only. Public API exposure still requires explicit field-level consent.
- `balance_monthly`, `budgets_monthly`, `metrics_monthly`, `stats`, and `stats_history` remain golden comparison data; they are not loaded as runtime calculations.
- All 31 L3 tables have one explicit decision: 16 runtime allowlisted, 5 golden-only, and 10 excluded. `balance_monthly` and `budgets_monthly` are also golden-only derived results.
- The 27 deterministic export payloads have source-controlled SHA-256 values. A load rejects a
  same-count byte change as well as a stale source manifest.
- Account-opening and first-buy quests, coupon catalogs, random boxes, and old external rewards are rejected.
- Only deterministic cosmetics are part of the product catalog.
- Locked Korean context values are normalized explicitly before runtime use: `정기` becomes
  `REGULAR`; `1인가구`, `고시원`, and `쉐어하우스` become `RENT`; parent cohabitation
  becomes `WITH_FAMILY`.

## Run

```bash
python3 -m venv .venv-import
.venv-import/bin/pip install -r tools/finmate_data_import/requirements.txt

.venv-import/bin/python tools/finmate_data_import/finmate_import.py export \
  --archive /path/to/finmate-v3-bundles.tar.zst \
  --source-root /path/to/unpacked/v1.0.0-bundles \
  --l3-source-root /path/to/finmate-data-at-eab7f87 \
  --output-dir /tmp/finmate-v1-seed

.venv-import/bin/python tools/finmate_data_import/finmate_import.py load \
  --input-dir /tmp/finmate-v1-seed \
  --database-url postgresql://finmate:finmate@localhost:5432/finmate
```

After the API migrations have run, the same steps and the runtime projection checks can be
executed as one explicit command:

```bash
.venv-import/bin/python tools/finmate_data_import/finmate_import.py bootstrap \
  --archive /path/to/finmate-v3-bundles.tar.zst \
  --source-root /path/to/unpacked/v1.0.0-bundles \
  --l3-source-root /path/to/finmate-data-at-eab7f87 \
  --output-dir /tmp/finmate-v1-seed \
  --database-url postgresql://finmate:finmate@localhost:5432/finmate
```

`verify-runtime` can be run independently to check provenance, projection version, row counts,
the 141 intentionally insufficient personas, and the absence of exact-value public projections.

The `export` command requires exactly the 31 locked L3 parquet files and verifies a canonical tree
digest built from each sorted relative path and file SHA-256. The `load` command refuses an export
whose archive, bundle commit, L3 commit, or L3 tree digest is stale. Every table uses a stable primary
key and `ON CONFLICT`. Runtime L3 is a complete release snapshot: the previous snapshot is deleted
and the approved files are reinserted in the same transaction, so a corrected payload cannot remain
beside stale payload-derived keys.

The verified v1.0.0 export contains 2,000 personas, 887,002 financial activities, 4,000 product
holdings, 8,912 investment holdings, 16,576 trades, 845,203 runtime L3 rows, and 444,000
golden-only rows. Golden rows are written under `golden_l3/` and are never included in PostgreSQL
seed operations. The corrected `friend_group_stats` retains `scored_friend_count`; all 1,832 unlocked
groups have three defined averages, while the 141 personas with non-positive disposable income keep
their individual ratio-based stats undefined instead of receiving invented zero scores.

A complete bootstrap produces 2,000 feature profiles, 2,000 behavior profiles, 388,000 daily
budgets, 11 group profiles, 3,939 approved routines, 28,040 friend relationships, 10,000
amount-free feed events, and 27,660 streak rows. Initial exact-value public projections remain
empty; test-created disclosure projections are not source data.

## Tests

```bash
python3 -m unittest discover -s tools/finmate_data_import/tests -v
```
