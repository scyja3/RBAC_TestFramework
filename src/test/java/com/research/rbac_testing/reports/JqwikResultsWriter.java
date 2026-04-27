package com.research.rbac_testing.reports;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class JqwikResultsWriter {

    public static final String RUN_FILE =
            "target/mr-results/jqwik_results_" +
                    new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                            .format(new Date(ManagementFactory.getRuntimeMXBean().getStartTime())) + ".csv";

    private static volatile boolean headerWritten = false;

    // prevents any MR IDs duplication across suite re-runs
    private static final Set<String> writtenIds = Collections.synchronizedSet(new HashSet<>());

    public static synchronized void write(
            String mrId, String category, String bugCsv,
            boolean passed, long durationMs) {
        // skip any duplicates
        if (!writtenIds.add(mrId)) return;

        new java.io.File("target/mr-results").mkdirs();

        try (FileWriter fw = new FileWriter(RUN_FILE, true)) {
            if (!headerWritten) {
                fw.write("mr_id,category,bug_types,result,duration_ms,notes\n");
                headerWritten = true;
            }
            fw.write(String.join(",",
                    mrId,
                    category,
                    bugCsv,
                    passed ? "PASS" : "FAIL",
                    String.valueOf(durationMs),
                    passed ? "" : "assertion_failed") + "\n");
        } catch (IOException e) {
            System.err.println(">>> JqwikResultsWriter failed for " + mrId + ": " + e.getMessage());
        }

        System.out.println(">>> MR recorded: " + mrId + " " + (passed ? "PASS" : "FAIL") + " (" + durationMs + "ms)");
    }

    private JqwikResultsWriter() {}
}
