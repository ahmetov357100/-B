# Заметки по оптимизации DZ_TODO_LIST_RESTR

Дата: 2026-04-29

- Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)
- Baseline: `DZ_TODO_LIST_RESTR v0.sql`
- Optimized: `DZ_TODO_LIST_RESTR v1.sql`
- Проверка: `row_count=349`, ожидалось `totalCount=349`
- Основная идея: ранний materialized CTE `rstr_pcz` фильтрует workflow templates `15T_RSTR` / `DZ-RSTR`, связывает их с `CI_TD_ENTRY_CHA` по `PCZ-EVT`, затем идет в `CI_TD_ENTRY` по primary key.
- Фильтрация `TYPE_DOG_GROUP` перенесена раньше через `acct_type`.
- Optional predicate `EXECUTOR` обернут в FreeMarker.
- Метрики: `buffer_gets 150638 -> 40178`, `disk_reads 107003 -> 69`, `oracle_elapsed_ms 1911 -> 1195`.
