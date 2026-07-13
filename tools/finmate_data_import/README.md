# FinMate synthetic-data importer

This tool is the product-side trust boundary for `gaga-studio/finmate-data` `v1.0.0`.
It verifies the release archive, exports only approved fields, and idempotently upserts the
transformed seed into PostgreSQL. The 937 MB unpacked source bundle is never committed here.

The immutable L1/L2 bundle and the independently corrected L3 tree have separate provenance:

- bundle commit: `63ca3d046eba9ec510e377a28a0083233aefff61`
- L3 commit: `22243bce34131737fc762675f0817ead08bc165a`
- L3 tree SHA-256: `dd30ae91f5e517f2a502ce46b2dfe3666107562e2dc52edb25fec48b893c3c33`

## Safety boundary

- Raw account numbers, transaction memos, API references, detailed employers, and detailed locations are discarded.
- Product names, holdings, and trades are imported into private staging tables only. Public API exposure still requires explicit field-level consent.
- `balance_monthly`, `budgets_monthly`, `metrics_monthly`, `stats`, and `stats_history` remain golden comparison data; they are not loaded as runtime calculations.
- All 31 L3 tables have one explicit decision: 16 runtime allowlisted, 5 golden-only, and 10 excluded. `balance_monthly` and `budgets_monthly` are also golden-only derived results.
- The 27 deterministic export payloads have source-controlled SHA-256 values. A load rejects a
  same-count byte change as well as a stale source manifest.
- Account-opening and first-buy quests, coupon catalogs, random boxes, and old external rewards are rejected.
- Only deterministic cosmetics are part of the product catalog.

## Run

```bash
python3 -m venv .venv-import
.venv-import/bin/pip install -r tools/finmate_data_import/requirements.txt

.venv-import/bin/python tools/finmate_data_import/finmate_import.py export \
  --archive /path/to/finmate-v3-bundles.tar.zst \
  --source-root /path/to/unpacked/v1.0.0-bundles \
  --l3-source-root /path/to/finmate-data-at-22243bc \
  --output-dir /tmp/finmate-v1-seed

.venv-import/bin/python tools/finmate_data_import/finmate_import.py load \
  --input-dir /tmp/finmate-v1-seed \
  --database-url postgresql://finmate:finmate@localhost:5432/finmate
```

The `export` command requires exactly the 31 locked L3 parquet files and verifies a canonical tree
digest built from each sorted relative path and file SHA-256. The `load` command refuses an export
whose archive, bundle commit, L3 commit, or L3 tree digest is stale. Every table uses a stable primary
key and `ON CONFLICT`. Runtime L3 is a complete release snapshot: the previous snapshot is deleted
and the approved files are reinserted in the same transaction, so a corrected payload cannot remain
beside stale payload-derived keys.

The verified v1.0.0 export contains 2,000 personas, 887,002 financial activities, 4,000 product
holdings, 8,912 investment holdings, 16,576 trades, 845,202 runtime L3 rows, and 444,000
golden-only rows. Golden rows are written under `golden_l3/` and are never included in PostgreSQL
seed operations. The corrected `friend_group_stats` retains `scored_friend_count`; all 1,832 unlocked
groups have three defined averages, while the 141 personas with non-positive disposable income keep
their individual ratio-based stats undefined instead of receiving invented zero scores.

## Tests

```bash
python3 -m unittest discover -s tools/finmate_data_import/tests -v
```
