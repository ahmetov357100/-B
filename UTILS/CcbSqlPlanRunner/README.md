# CcbSqlPlanRunner

Java CLI helper for Oracle CC&B SQL tuning runs. It executes one SQL file twice, counts rows, finds the latest cursor by the marker comment, fetches `DBMS_XPLAN.DISPLAY_CURSOR`, and writes run artifacts.

## Layout

```text
src/             Java sources
sql/<report>/    SQL versions under test, grouped by report/query
config/          local connection files
runs/<report>/   saved run outputs, grouped by report/query
build/classes/   compiled classes
docs/            original notes and prompts
temp/            disposable checks and scratch files
```

Current report workspaces:

```text
sql/DZ_TODO_LIST_CLAIM/
runs/DZ_TODO_LIST_CLAIM/

sql/CI_TD_ENTRY_CHA_CHAR_TYPE_COUNTS/
runs/CI_TD_ENTRY_CHA_CHAR_TYPE_COUNTS/
```

## Build

```bat
cd C:\MyGPT\CCB\UTILS\CcbSqlPlanRunner
javac -d build\classes src\*.java
```

## Run

```bat
set CP=build\classes;C:\oracle\sqlcl\lib\ojdbc11.jar;C:\oracle\sqlcl\lib\orai18n.jar
java -cp "%CP%" CcbSqlPlanRunner "sql\DZ_TODO_LIST_CLAIM\DZ_TODO_LIST_CLAIM v5.sql" config\tnsnames.txt runs\DZ_TODO_LIST_CLAIM\out_final_v5
```

Output files:

```text
result_vN.txt
count_vN.txt
sql_id_vN.txt
plan_vN.txt
metrics_vN.json
query_rendered_vN.sql
```

`query_rendered_vN.sql` is written only when the input SQL uses the supported FreeMarker subset.

## Notes

The SQL file must contain a leading marker comment, for example:

```sql
--MY_QUERY_MARKER v5
```

Named binds such as `:USER_ID` are supported. Bind values can come from `bind.NAME=value` entries in the connection file or from the leading JSON comment in the SQL file. If `USER_ID` is referenced and not provided, the runner uses `TEK1TST`.

Keep SQL iterations and their outputs under matching report folders in `sql/` and `runs/`.

Use `temp/` for disposable checks, ad hoc SQL, copied snippets, and files that can be deleted without losing optimization history.
