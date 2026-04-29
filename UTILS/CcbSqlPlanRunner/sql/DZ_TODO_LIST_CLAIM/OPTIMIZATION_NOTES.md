# Заметки по оптимизации DZ_TODO_LIST_CLAIM

Дата: 2026-04-29

- Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)
- Финальный принятый SQL: `DZ_TODO_LIST_CLAIM v5.sql`
- Финальные артефакты: `../../runs/DZ_TODO_LIST_CLAIM/out_final_v5`
- Проверка: `row_count=313`
- Основная идея: ранний materialized CTE `prelaw_pcz` фильтрует workflow template `13T_PRELAW`, связывает workflow с `CI_TD_ENTRY_CHA` по `PCZ-EVT`, затем идет в `CI_TD_ENTRY` по primary key.
- Добавлен FreeMarker rendering для optional predicates и bind-dependent SQL branches.
- `v6` оставлен как экспериментальный вариант с более ранней фильтрацией account type.
