# Workspace Structure

## Goal

This workspace is organized for two main activities:

1. Oracle SQL optimization.
2. Enhancement and support of existing CC&B reports.

The current repository already contains useful source material, but it mixes reference data, working SQL, reports, and one-off scripts. The structure below adds a dedicated execution layer for iterative tuning without breaking the existing folders.

## Current Folder Roles

- `DB` - schema snapshots, DDL, packages, views, procedures, and other Oracle reference artifacts.
- `knowledge` - project knowledge base: domain notes, tasks, recipes, and report explanations.
- `reports` - actual report XML files and report-specific working area.
- `SCRIPTS` - legacy SQL archive, extracts, templates, checks, and one-off utilities.
- `JAVA_CORE` - large Java reference codebase, useful only for targeted lookups.
- `DOCUMENTS` - external business and migration documents.

## Added Working Layer

- `reference` - normalized entry point for long-lived reference materials.
- `automation` - scripts and templates for repeatable report execution and tuning.
- `output` - aggregated output files when a run is not tied to one report folder.
- `tmp` - temporary local artifacts that can be regenerated.
- `archive` - old or frozen materials that should not participate in active work.

## Report-Centric Workflow

Each report should have its own folder:

```text
reports/<REPORT_NAME>/
  source/
  baseline/
  iterations/
  runs/
  analysis/
  notes/
```

Meaning:

- `source` - active XML, extracted SQL, parameters, and metadata.
- `baseline` - original query and baseline metrics before optimization.
- `iterations` - each next query version after tuning changes.
- `runs` - timestamped execution artifacts.
- `analysis` - structured findings and comparisons between runs.
- `notes` - report-specific context, assumptions, and open questions.

## Run Artifact Standard

Each execution should produce a dedicated folder:

```text
runs/YYYY-MM-DD_HH-mm-ss/
  input.sql
  execution_wrapper.sql
  execution_plan.txt
  result.csv
  metrics.json
  runner.log
```

Optional files:

- `result_preview.txt`
- `analysis.md`
- `next_query.sql`
- `diff_from_previous.sql`

## Proposed Migration Direction

Do not immediately move existing source folders. Use the following rules during normal work:

- Read schema and DDL from `DB`.
- Read historical project understanding from `knowledge`.
- Treat `SCRIPTS` as a legacy archive unless a file is promoted into a report-specific folder.
- Keep actual report optimization work inside `reports/<REPORT_NAME>/...`.
- Put reusable automation only under `automation`.

## Practical Rules

- One optimization task should modify one report folder at a time.
- Every query iteration must be saved as a separate file.
- Never overwrite baseline artifacts.
- Execution plans and sample results must be stored together with the query version that produced them.
- If a script becomes reusable, move it from ad hoc work into `automation/templates` or `automation/runner`.
