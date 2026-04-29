# Заметки по оптимизации DZ_TODO_LIST_LIM

Дата: 2026-04-29

- Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)

## Версии

- Baseline: `DZ_TODO_LIST_LIM v0.sql`
- Optimized: `DZ_TODO_LIST_LIM v1.sql`
- Артефакты baseline: `../../runs/DZ_TODO_LIST_LIM/out_base_v0`
- Артефакты optimized: `../../runs/DZ_TODO_LIST_LIM/out_opt_v1`

## Проверка результата

```text
expected totalCount = 161
v0 row_count = 161
v1 row_count = 161
```

## Суть оптимизации

- Добавлен ранний materialized CTE `lim_pcz`.
- `lim_pcz` фильтрует `CI_WF_PROC` по `wf_proc_tmpl_cd in ('10T_NTFPAY', '11T_LIMIT')`.
- До большого join-дерева CTE связывает workflow с `CI_TD_ENTRY_CHA` по `PCZ-EVT`.
- После сужения набора отчет идет в `CI_TD_ENTRY` по primary key.
- Optional-фильтры `MAIN_NAME` и `EXECUTOR` обернуты в FreeMarker, чтобы пустые параметры не создавали бесполезные predicates и missing bind warnings.

## Изменение плана

В `v0` был поздний широкий доступ к `CI_TD_ENTRY_CHA`, включая `TABLE ACCESS FULL CI_TD_ENTRY_CHA` внутри большого join-дерева.

В `v1` используется `TEMP TABLE TRANSFORMATION` / `LOAD AS SELECT` для `lim_pcz` и индексный доступ к `CI_TD_ENTRY_CHA` (`XT701S2`) для пути `PCZ-EVT`.

## Метрики

```text
oracle_elapsed_ms: 2230 -> 999
oracle_cpu_ms:     2228 -> 999
buffer_gets:     140750 -> 13242
disk_reads:      107003 -> 0
java run2 ms:      2297 -> 1050
```
