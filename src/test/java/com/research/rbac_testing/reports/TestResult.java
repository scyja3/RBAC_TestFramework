package com.research.rbac_testing.reports;

/*
* Records of a single MR test execution
* It maps directly to columns in the CSV output
* */
public class TestResult {

    public final String mrId;
    public final String mrCategory;         // Inheritance, Monotonicity, and Equivalence
    public final String bugTypes;           // format: e.g. "PRIVILEGE_LEAKAGE | PERMISSION_LOSS"
    public final String testMethodName;
    public final boolean passed;
    public final long durationMs;
    public final String failureMessage;     // empty string when passed

    public TestResult(String mrId,
                      String mrCategory,
                      String bugTypes,
                      String testMethodName,
                      boolean passed,
                      long durationMs,
                      String failureMessage) {
        this.mrId = mrId;
        this.mrCategory = mrCategory;
        this.bugTypes = bugTypes;
        this.testMethodName = testMethodName;
        this.passed = passed;
        this.durationMs = durationMs;
        this.failureMessage = failureMessage;
    }

    // the CSV row, should match with the header in TestReport.java
    public String toCsvRow() {
        return String.join(",",
                escapeCsv(mrId),
                escapeCsv(mrCategory),
                escapeCsv(bugTypes),
                escapeCsv(testMethodName),
                passed ? "PASS" : "FAIL",
                String.valueOf(durationMs),
                escapeCsv(failureMessage)
        );
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        // if the value contains any commas, quotes, or new lines, wrap it in quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
