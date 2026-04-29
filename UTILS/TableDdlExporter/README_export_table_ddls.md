# Export Oracle Table DDLs From List

## Files

Put these files next to `run_export_table_ddls.bat`:

- `table_list.txt` - list of tables;
- `tnsnames.txt` - connection settings.

`tnsnames.txt` may contain either `jdbc_url=...` or a `tnsnames.ora`-style block with `HOST`, `PORT`, and `SERVICE_NAME`.

Required keys:

```text
user=READONLY_CCB
password=Readonly_12345
owner=CISADM
```

## Run

Default run from `C:\MyGPT\CCB\output`:

```bat
run_export_table_ddls.bat
```

Run with explicit files and output folder:

```bat
run_export_table_ddls.bat C:\MyGPT\CCB\input\table_list.txt C:\MyGPT\CCB\output\tnsnames.txt C:\MyGPT\CCB\DB\CISADM\TABLES
```

## Result

For each table the utility creates:

```text
<TABLE_NAME>.txt
```

The utility also creates:

```text
ddl_export_summary.txt
```

Exit code:

- `0` - all tables exported and verified;
- `2` - one or more tables failed verification or export.

## Charset

The run script always includes both Oracle jars:

```text
C:\oracle\sqlcl\lib\ojdbc11.jar
C:\oracle\sqlcl\lib\orai18n.jar
```

`orai18n.jar` is required because the database charset is `CL8ISO8859P5`.
