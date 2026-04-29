# Журнал работ по CC&B

Проектный журнал для работ в этом workspace. Сюда пишем не только SQL tuning, но и разработку отчетов, утилиты, разбор данных, миграции и другие задачи по CC&B.

Формат записей держим коротким и практичным:

- дата
- область / компонент
- цель
- измененные артефакты
- проверка результата
- заметки / что помнить дальше

## 2026-04-29 - Наведение порядка в CcbSqlPlanRunner

Область: `UTILS/CcbSqlPlanRunner`

Цель:

- Восстановить контекст по Java-утилите запуска SQL.
- Разложить файлы так, чтобы будущие оптимизации отчетов не смешивали SQL-версии и результаты прогонов.

Артефакты:

- Исходники перенесены в `UTILS/CcbSqlPlanRunner/src`
- SQL сгруппированы по отчетам в `UTILS/CcbSqlPlanRunner/sql/<report>`
- Результаты прогонов сгруппированы в `UTILS/CcbSqlPlanRunner/runs/<report>`
- Для одноразовых проверок добавлена папка `UTILS/CcbSqlPlanRunner/temp`
- Добавлен README утилиты: `UTILS/CcbSqlPlanRunner/README.md`

Проверка:

- Компиляция Java прошла: `javac -d build\classes src\*.java`.

Заметки:

- Runner сохраняет `result_vN.txt`, `count_vN.txt`, `sql_id_vN.txt`, `plan_vN.txt`, `metrics_vN.json`.
- Если SQL использует FreeMarker, дополнительно сохраняется rendered SQL.

## 2026-04-29 - Пакет оптимизаций DZ TODO отчетов

Область: `UTILS/CcbSqlPlanRunner/sql`

Связанная задача: `HT-16114` (`https://jira.sigma-it.ru/browse/HT-16114`)

Цель:

- Оптимизировать SQL вариантов CC&B TODO отчетов без изменения количества строк.
- Снять планы Oracle и метрики для baseline и optimized версий.

Отчеты:

- `DZ_TODO_LIST_CLAIM`
- `DZ_TODO_LIST_RESTR`
- `DZ_TODO_LIST_PRELAW`
- `DZ_TODO_LIST_LIM`

Артефакты:

- Детальный tuning-журнал: `UTILS/CcbSqlPlanRunner/docs/optimization_history.md`
- Заметки по каждому отчету: `UTILS/CcbSqlPlanRunner/sql/<report>/OPTIMIZATION_NOTES.md`
- Планы и результаты: `UTILS/CcbSqlPlanRunner/runs/<report>/out_*`

Общий паттерн оптимизации:

- Вынести выборку нужных workflow в ранний materialized CTE.
- Соединить `CI_WF_PROC` с `CI_TD_ENTRY_CHA` по `PCZ-EVT`.
- После сужения набора идти в `CI_TD_ENTRY` по primary key.
- Оборачивать неиспользуемые optional predicates в FreeMarker, чтобы пустые параметры не порождали бесполезные условия и missing bind warnings.

Проверка:

- `DZ_TODO_LIST_CLAIM`: финальная принятая версия, `row_count=313`
- `DZ_TODO_LIST_RESTR`: `row_count=349`, ожидалось `totalCount=349`
- `DZ_TODO_LIST_PRELAW`: `row_count=245`, ожидалось `totalCount=245`
- `DZ_TODO_LIST_LIM`: `row_count=161`, ожидалось `totalCount=161`

Ключевая заметка по `DZ_TODO_LIST_LIM`:

- В `v0` был поздний широкий доступ к `CI_TD_ENTRY_CHA`, включая `TABLE ACCESS FULL CI_TD_ENTRY_CHA`.
- В `v1` добавлен materialized CTE `lim_pcz` и индексный доступ к `CI_TD_ENTRY_CHA` для пути `PCZ-EVT`.
- `MAIN_NAME` и `EXECUTOR` обернуты в FreeMarker, потому что это optional inputs.

## 2026-04-29 - Подготовка безопасного GitHub sync

Область: корень проекта `C:\MyGPT\CCB`

Цель:

- Подготовить workspace к сохранению важных инженерных артефактов в GitHub без утечки SQL, результатов прогонов и подключений.

Артефакты:

- Добавлен `.gitignore`
- Добавлен безопасный шаблон подключения: `UTILS/CcbSqlPlanRunner/config/tnsnames.example.txt`
- Добавлена политика синхронизации: `docs/github_sync.md`
- В allow-list добавлены исходники и запускные bat-файлы утилит `PackageDdlExporter` и `TableDdlExporter`

Решение:

- В GitHub сохраняем утилиты, исходники, README, журналы и `OPTIMIZATION_NOTES.md`.
- Не сохраняем реальные `tnsnames.txt`, рабочие SQL отчетов, `runs/`, `build/`, `temp/`, входные/выходные данные и локальные выгрузки.

Текущая точка настройки:

- Git for Windows найден не через `PATH`, а по полному пути: `C:\Program Files\Git\cmd\git.exe`.
- Целевой GitHub repo пользователя: `https://github.com/ahmetov357100/-B`.
- Следующий шаг: проверить, является ли `C:\MyGPT\CCB` git-репозиторием, затем при необходимости выполнить `git init`, настроить `remote origin` и сделать первый commit/push.
- Для доступа к GitHub предполагается использовать установленный Git Credential Manager. При первом `git push` он должен открыть browser/login или использовать уже сохраненные credentials.
- Важно перед `git add` проверить, что allow-list не включает секреты: `tnsnames.txt`, рабочие SQL, `runs/`, `build/`, `test_output`.

Статус после настройки:

- Выполнен `git init` в `C:\MyGPT\CCB`.
- Настроен remote: `origin https://github.com/ahmetov357100/-B`.
- Remote `origin/main` существует и содержит свои файлы, поэтому локальная работа сохранена в отдельной ветке `ccb-utils-sync`, чтобы не рисковать main.
- Создан локальный commit в ветке `ccb-utils-sync` с сообщением `Add CCB utility sources and work journal`.
- В commit попали только allow-list файлы: журналы, README, Java-исходники `CcbSqlPlanRunner`, `PackageDdlExporter`, `TableDdlExporter`, `tnsnames.example.txt`, `OPTIMIZATION_NOTES.md`.
- Попытка `git push -u origin ccb-utils-sync` из Codex-среды не прошла: Git Credential Manager не смог интерактивно запросить credentials (`/dev/tty` недоступен), также было сообщение `ServicePointManager не поддерживает прокси со схемой socks5`.
- Для продолжения нужно выполнить push из обычного интерактивного PowerShell/Git Bash под пользователем Windows или предварительно авторизовать Git Credential Manager.
