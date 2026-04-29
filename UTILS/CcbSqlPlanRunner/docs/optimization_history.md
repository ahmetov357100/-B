# Журнал SQL-оптимизаций

Короткие заметки по SQL tuning сессиям. Формат: отчет, дата, версии, проверка результата и основная идея оптимизации.

Общий проектный журнал лежит в `../../../docs/work_history.md`. Этот файл хранит детали именно по SQL tuning в `CcbSqlPlanRunner`.

## 2026-04-29 - DZ_TODO_LIST_CLAIM

- Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)
- SQL: `sql/DZ_TODO_LIST_CLAIM/DZ_TODO_LIST_CLAIM v5.sql`
- Результаты: `runs/DZ_TODO_LIST_CLAIM/out_final_v5`
- Проверка: `row_count=313`
- Основное изменение: выборка нужных workflow вынесена в ранний materialized CTE `prelaw_pcz` через `CI_WF_PROC` + `CI_TD_ENTRY_CHA` / `PCZ-EVT`; затем `CI_TD_ENTRY` цепляется по primary key.
- Дополнительно: FreeMarker используется для отключения неиспользуемых optional predicates; rendered SQL сохраняется для анализа плана.
- Итог: финальной принятой версией была `v5`; `v6` оставлен как экспериментальный вариант.

## 2026-04-29 - DZ_TODO_LIST_RESTR

- Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)
- SQL: `sql/DZ_TODO_LIST_RESTR/DZ_TODO_LIST_RESTR v1.sql`
- Baseline: `runs/DZ_TODO_LIST_RESTR/out_base_v0`
- Optimized: `runs/DZ_TODO_LIST_RESTR/out_opt_v1`
- Проверка: `row_count=349`, ожидалось `totalCount=349`
- Основное изменение: добавлен ранний materialized CTE `rstr_pcz` для `wf_proc_tmpl_cd in ('15T_RSTR', 'DZ-RSTR')`; workflow связывается с `CI_TD_ENTRY_CHA` по `PCZ-EVT`, затем `CI_TD_ENTRY` идет по PK.
- Дополнительно: фильтрация `TYPE_DOG_GROUP` перенесена раньше через `acct_type`; optional `EXECUTOR` обернут в FreeMarker.
- Метрики: `buffer_gets 150638 -> 40178`, `disk_reads 107003 -> 69`, `oracle_elapsed_ms 1911 -> 1195`.

## 2026-04-29 - DZ_TODO_LIST_PRELAW

- Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)
- SQL: `sql/DZ_TODO_LIST_PRELAW/DZ_TODO_LIST_PRELAW v1.sql`
- Baseline: `runs/DZ_TODO_LIST_PRELAW/out_base_v0`
- Optimized: `runs/DZ_TODO_LIST_PRELAW/out_opt_v1`
- Проверка: `row_count=245`, ожидалось `totalCount=245`
- Основное изменение: добавлен ранний materialized CTE `prelaw_pcz` для `wf_proc_tmpl_cd = 'TMPL-PRE-SET'`; workflow связывается с `CI_TD_ENTRY_CHA` по `PCZ-EVT`, затем `CI_TD_ENTRY` идет по PK.
- Дополнительно: фильтрация `TYPE_DOG_GROUP` перенесена раньше через `acct_type`; optional `EXECUTOR` обернут в FreeMarker.
- Метрики: `buffer_gets 155386 -> 32251`, `disk_reads 107003 -> 0`, `oracle_elapsed_ms 2123 -> 1284`.

## 2026-04-29 - DZ_TODO_LIST_LIM

- Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)
- SQL: `sql/DZ_TODO_LIST_LIM/DZ_TODO_LIST_LIM v1.sql`
- Baseline: `runs/DZ_TODO_LIST_LIM/out_base_v0`
- Optimized: `runs/DZ_TODO_LIST_LIM/out_opt_v1`
- Проверка: `row_count=161`, ожидалось `totalCount=161`
- Основное изменение: добавлен ранний materialized CTE `lim_pcz` для `wf_proc_tmpl_cd in ('10T_NTFPAY', '11T_LIMIT')`; это убрало поздний широкий доступ к `CI_TD_ENTRY_CHA` по пути `PCZ-EVT` и позволило идти в `CI_TD_ENTRY` по primary key.
- Изменение плана: в `v0` был `TABLE ACCESS FULL CI_TD_ENTRY_CHA` внутри большого дерева join-ов; в `v1` сначала materialize workflow subset, затем индексный доступ к `CI_TD_ENTRY_CHA` (`XT701S2`) для `PCZ-EVT`.
- Дополнительно: optional `MAIN_NAME` и `EXECUTOR` обернуты в FreeMarker; исчезло `missing_bind_values=[MAIN_NAME, EXECUTOR]`.
- Метрики: `buffer_gets 140750 -> 13242`, `disk_reads 107003 -> 0`, `oracle_elapsed_ms 2230 -> 999`.
