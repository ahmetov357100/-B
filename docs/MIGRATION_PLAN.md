# Migration Plan

## Objective

Introduce a predictable workspace for Oracle report tuning without breaking the existing project archive.

## Phase 1

Create the new structure and keep all old folders in place.

Done in this phase:

- created `docs`
- created `reference`
- created `automation`
- created `output`
- created `tmp`
- created `archive`
- created per-report work folders for:
  - `FTO_DEBT_TRANSFER_DETAIL_LIST_ALL`
  - `FTO_DEBT_TRANSFER_DZ_SOURCE_LIST`

## Phase 2

Populate each report folder with:

- the active XML file
- extracted SQL
- baseline metrics
- notes about bind variables and business constraints

Recommended target:

```text
reports/FTO_DEBT_TRANSFER_DETAIL_LIST_ALL/source/report.xml
reports/FTO_DEBT_TRANSFER_DETAIL_LIST_ALL/source/query.sql
reports/FTO_DEBT_TRANSFER_DETAIL_LIST_ALL/baseline/baseline_metrics.json
reports/FTO_DEBT_TRANSFER_DETAIL_LIST_ALL/notes/context.md
```

Same pattern for the second report.

## Phase 3

Promote useful legacy SQL from `SCRIPTS`:

- report extracts into report folders
- reusable wrappers into `automation/templates`
- one-time historic files into `archive` or `reference/legacy_scripts`

## Phase 4

Run iterative tuning through the automation wrapper:

1. select report folder
2. select query file
3. execute through SQL*Plus
4. save plan, result, metrics, log
5. analyze and create next iteration
6. compare with baseline

## Naming Conventions

- report folder: exact report code
- query iterations: `query.v001.sql`, `query.v002.sql`, ...
- run folders: `YYYY-MM-DD_HH-mm-ss`
- analysis files: `analysis.v001.md`, `analysis.v002.md`, ...

## Risks To Address Later

- inconsistent file encodings in legacy text files
- mixed Russian and English folder naming in legacy materials
- `index.json` is not a plain JSON document and should be documented separately
- `JAVA_CORE` is too large for routine scans and should remain reference-only
