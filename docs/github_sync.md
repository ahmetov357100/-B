# GitHub Sync Policy

Репозиторий должен хранить воспроизводимую инженерную работу, а не локальный дамп workspace.

## Сохраняем

- `docs/work_history.md`
- `docs/github_sync.md`
- `UTILS/CcbSqlPlanRunner/README.md`
- `UTILS/CcbSqlPlanRunner/src/*.java`
- `UTILS/CcbSqlPlanRunner/docs/optimization_history.md`
- `UTILS/CcbSqlPlanRunner/config/tnsnames.example.txt`
- `UTILS/CcbSqlPlanRunner/sql/*/OPTIMIZATION_NOTES.md`
- `UTILS/PackageDdlExporter/*.java`
- `UTILS/PackageDdlExporter/*.md`
- `UTILS/PackageDdlExporter/*.bat`
- `UTILS/TableDdlExporter/*.java`
- `UTILS/TableDdlExporter/*.md`
- `UTILS/TableDdlExporter/*.bat`

## Не сохраняем

- реальные подключения и пароли: `tnsnames.txt`, `tns/`, `.env`
- рабочие SQL оптимизируемых отчетов: `UTILS/CcbSqlPlanRunner/sql/**/*.sql`
- результаты прогонов и планы: `UTILS/CcbSqlPlanRunner/runs/`
- build artifacts: `build/`, `*.class`
- DDL exporter outputs: `UTILS/*DdlExporter/test_output/`
- локальные списки объектов и подключения: `package_list.txt`, `table_list.txt`, `tnsnames.txt`
- временные файлы: `temp/`, `tmp/`, `output/`, `_logs/`
- локальные входные данные, выгрузки, DDL dumps, reference-директории
- любые новые файлы по умолчанию, пока они явно не разрешены в `.gitignore`

## Правило

Если файл помогает восстановить подход, код или решение задачи, его можно хранить.

Если файл содержит данные заказчика, реальные подключения, большие выгрузки, временные результаты или рабочий SQL с потенциально чувствительной бизнес-логикой, он остается локально.
