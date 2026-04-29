# Export Oracle Package DDLs From List

## Run

```bat
C:\MyGPT\CCB\output\run_export_package_ddls.bat C:\MyGPT\CCB\input\package_list.txt C:\MyGPT\CCB\output\tnsnames.txt C:\MyGPT\CCB\DB\CISADM\PACKAGES
```

## Input

`package_list.txt` may use the current semicolon CSV format:

```text
"   ";"PACKAGE_NAME"
"1";"CISADM.ADJUSTMENT"
```

or a simple list:

```text
CISADM.ADJUSTMENT
CM_ACCOUNT
```

## Output

For each package:

```text
<PACKAGE_NAME>.txt
```

Summary:

```text
package_ddl_export_summary.txt
```

## Sections

Each package file contains:

- `OBJECT VISIBILITY CHECK`
- `PACKAGE SPEC DDL`
- `PACKAGE BODY DDL`
- `PACKAGE SOURCE FROM ALL_SOURCE`
- `DEPENDENCIES`
- `ERRORS`

The utility uses one Java process and one Oracle connection for the whole package list.
