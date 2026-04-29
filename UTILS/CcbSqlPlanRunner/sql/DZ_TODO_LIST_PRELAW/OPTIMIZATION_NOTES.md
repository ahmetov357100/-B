# Заметки по оптимизации DZ_TODO_LIST_PRELAW

Дата: 2026-04-29

- Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)
- Baseline: `DZ_TODO_LIST_PRELAW v0.sql`
- Optimized: `DZ_TODO_LIST_PRELAW v1.sql`
- Проверка: `row_count=245`, ожидалось `totalCount=245`
- Основная идея: ранний materialized CTE `prelaw_pcz` фильтрует workflow template `TMPL-PRE-SET`, связывает workflow с `CI_TD_ENTRY_CHA` по `PCZ-EVT`, затем идет в `CI_TD_ENTRY` по primary key.
- Фильтрация `TYPE_DOG_GROUP` перенесена раньше через `acct_type`.
- Optional predicate `EXECUTOR` обернут в FreeMarker.
- Метрики: `buffer_gets 155386 -> 32251`, `disk_reads 107003 -> 0`, `oracle_elapsed_ms 2123 -> 1284`.
