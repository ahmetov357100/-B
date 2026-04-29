final class SqlExecutionResult {
    final long rowsRun1;
    final long rowsRun2;
    final long rowCount;
    final long javaElapsedRun1Ms;
    final long javaElapsedRun2Ms;

    SqlExecutionResult(long rowsRun1, long rowsRun2, long rowCount,
                       long javaElapsedRun1Ms, long javaElapsedRun2Ms) {
        this.rowsRun1 = rowsRun1;
        this.rowsRun2 = rowsRun2;
        this.rowCount = rowCount;
        this.javaElapsedRun1Ms = javaElapsedRun1Ms;
        this.javaElapsedRun2Ms = javaElapsedRun2Ms;
    }
}
