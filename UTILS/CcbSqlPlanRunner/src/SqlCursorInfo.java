final class SqlCursorInfo {
    final String sqlId;
    final int childNumber;
    final long executions;
    final String lastActiveTime;
    final long elapsedTime;
    final long cpuTime;
    final long bufferGets;
    final long diskReads;
    final long rowsProcessed;
    final long fetches;
    final long parseCalls;
    final long loads;
    final long invalidations;
    final long planHashValue;

    SqlCursorInfo(String sqlId, int childNumber, long executions, String lastActiveTime,
                  long elapsedTime, long cpuTime, long bufferGets, long diskReads,
                  long rowsProcessed, long fetches, long parseCalls, long loads,
                  long invalidations, long planHashValue) {
        this.sqlId = sqlId;
        this.childNumber = childNumber;
        this.executions = executions;
        this.lastActiveTime = lastActiveTime;
        this.elapsedTime = elapsedTime;
        this.cpuTime = cpuTime;
        this.bufferGets = bufferGets;
        this.diskReads = diskReads;
        this.rowsProcessed = rowsProcessed;
        this.fetches = fetches;
        this.parseCalls = parseCalls;
        this.loads = loads;
        this.invalidations = invalidations;
        this.planHashValue = planHashValue;
    }
}
