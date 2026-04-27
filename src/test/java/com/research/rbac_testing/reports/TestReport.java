package com.research.rbac_testing.reports;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class TestReport {

    private static final TestReport INSTANCE = new TestReport();
    private final List<TestResult> results = Collections.synchronizedList(new ArrayList<>());

    private TestReport() {}

    public static TestReport getInstance() {
        return INSTANCE;
    }

    public void record(TestResult result) {
        results.add(result);
    }

    // the CSV Report
    public void writeReport() {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String rawCSVPath     = "target/mr-results/raw_results_"  + timestamp + ".csv";
        String summaryCSVPath = "target/mr-results/summary_" + timestamp + ".csv";

        new java.io.File("target/mr-results").mkdirs();

        // Fold the 30 jqwik @Property results into our list BEFORE writing anything
        mergeJqwikResults();

        writeRawCSV(rawCSVPath);
        writeSummaryCSV(summaryCSVPath);
        printConsoleReport();

        System.out.println("\n[MR Reporter] Raw Results: " + rawCSVPath);
        System.out.println("[MR Reporter] Summary: "     + summaryCSVPath);
    }

    // -----------------------------------------------------------------------
    // Reads jqwik_results_*.csv (written by JqwikResultsWriter) and adds
    // each row to the shared results list so the summary includes all 30 MRs
    // -----------------------------------------------------------------------
    private void mergeJqwikResults() {
        File jqwikFile = new File(JqwikResultsWriter.RUN_FILE);

        if (!jqwikFile.exists()) {
            System.err.println("[MR Reporter] WARNING: jqwik results file not found at "
                    + JqwikResultsWriter.RUN_FILE);
            return;
        }

        // Track IDs already in results so we don't add duplicates
        Set<String> existingIds = new HashSet<>();
        for (TestResult r : results) existingIds.add(r.mrId);

        int merged = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(jqwikFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("mr_id") || line.trim().isEmpty()) continue;

                // jqwik CSV columns: mr_id, category, bug_types, result, duration_ms, notes
                String[] cols = line.split(",", -1);
                if (cols.length < 5) continue;

                String  mrId   = cols[0].trim();
                String  cat    = cols[1].trim();
                String  bugs   = cols[2].trim();
                boolean passed = "PASS".equals(cols[3].trim());
                long    ms     = 0;
                try { ms = Long.parseLong(cols[4].trim()); }
                catch (NumberFormatException ignored) {}
                String note = cols.length > 5 ? cols[5].trim() : "";

                if (!existingIds.add(mrId)) continue; // skip duplicate
                results.add(new TestResult(mrId, cat, bugs, mrId, passed, ms, note));
                merged++;
            }
        } catch (IOException e) {
            System.err.println("[MR Reporter] Could not merge jqwik results: " + e.getMessage());
        }

        System.out.println("[MR Reporter] Merged " + merged + " jqwik MR results from "
                + jqwikFile.getName());
    }

    // -----------------------------------------------------------------------
    // Raw CSV — everything including SCALE rows, for SRQ3 complexity analysis
    // -----------------------------------------------------------------------
    private void writeRawCSV(String path) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            writer.println("mr_id, mr_category, bug_types, test_method, results, duration_ms, failure_message");
            results.forEach(r -> writer.println(r.toCsvRow()));
        } catch (IOException e) {
            System.err.println("[MR Reporter] Failed to write raw results: " + e.getMessage());
        }
    }

    // the Summary CSV (to help answer the research questions)
    private void writeSummaryCSV(String path) {
        // SCALE tests are complexity benchmarks for SRQ3, not MR pass/fail results.
        // Keeping them in the summary would dilute detection rates and distort RQ answers.
        List<TestResult> mrOnly = results.stream()
                .filter(r -> !r.mrId.contains("-SCALE"))
                .collect(Collectors.toList());

        if (mrOnly.isEmpty()) {
            System.err.println("[MR Reporter] WARNING: no MR results to summarise — mergeJqwikResults() may have failed");
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {

            // Section 1 — Main RQ: detection rate per category
            writer.println("MAIN RQ: the detection rate by each MR category");
            writer.println("category, total_tests, passed, failed, detection_rate_%, avg_duration_ms");

            Map<String, List<TestResult>> byCategory = mrOnly.stream()
                    .collect(Collectors.groupingBy(r -> r.mrCategory));

            byCategory.forEach((cat, list) -> {
                long passed = list.stream().filter(r -> r.passed).count();
                long failed = list.stream().filter(r -> !r.passed).count();
                double rate  = (failed * 100.0) / list.size();
                double avgMs = list.stream().mapToLong(r -> r.durationMs).average().orElse(0);
                writer.printf("%s, %d, %d, %d, %.1f, %.1f%n",
                        cat, list.size(), passed, failed, rate, avgMs);
            });

            writer.println();

            // Section 2 — SRQ1: detection rate per bug type per category
            writer.println("SRQ1: Detection rate by each bug type");
            writer.println("bug_type, category, tests_targeting, bugs_detected, detection_rate_%");

            List<Map.Entry<String, TestResult>> expanded = new ArrayList<>();
            for (TestResult r : mrOnly) {
                for (String bt : r.bugTypes.split("\\|")) {
                    expanded.add(new AbstractMap.SimpleEntry<>(bt.trim(), r));
                }
            }

            Map<String, Map<String, List<TestResult>>> byCategoryAndBugType =
                    expanded.stream().collect(
                            Collectors.groupingBy(
                                    e -> e.getValue().mrCategory,
                                    Collectors.groupingBy(
                                            Map.Entry::getKey,
                                            Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                                    )
                            )
                    );

            byCategoryAndBugType.forEach((cat, bugMap) ->
                    bugMap.forEach((bt, list) -> {
                        long detected = list.stream().filter(r -> !r.passed).count();
                        double rate   = (detected * 100.0) / list.size();
                        writer.printf("%s, %s, %d, %d, %.1f%n",
                                bt, cat, list.size(), detected, rate);
                    })
            );

            writer.println();

            // Section 3 — SRQ2: computational cost per category
            writer.println("SRQ2: Computational Cost by MR Category");
            writer.println("category, min_ms, max_ms, avg_ms, total_ms");

            byCategory.forEach((cat, list) -> {
                LongSummaryStatistics stats = list.stream()
                        .mapToLong(r -> r.durationMs)
                        .summaryStatistics();
                writer.printf("%s, %d, %d, %.1f, %d%n",
                        cat, stats.getMin(), stats.getMax(),
                        stats.getAverage(), stats.getSum());
            });

            writer.println();

            // Section 4 — per-MR detail table
            writer.println("Details for each MR");
            writer.println("mr_id, category, result, duration_ms, bug_types");
            mrOnly.forEach(r -> writer.printf("%s, %s, %s, %d, %s%n",
                    r.mrId, r.mrCategory,
                    r.passed ? "PASS" : "FAIL",
                    r.durationMs,
                    r.bugTypes));

        } catch (IOException e) {
            System.err.println("[MR Reporter] Failed to write summary: " + e.getMessage());
        }
    }

    // printing out the console summary
    private void printConsoleReport() {
        List<TestResult> mrOnly = results.stream()
                .filter(r -> !r.mrId.contains("-SCALE"))
                .collect(Collectors.toList());

        System.out.println("\nRBAC Metamorphic Testing Summary");
        long totalPassed = mrOnly.stream().filter(r -> r.passed).count();
        long totalFailed = mrOnly.stream().filter(r -> !r.passed).count();
        System.out.printf("Total MRs run:    %d%n", mrOnly.size());
        System.out.printf("Total MRs failed: %d%n", totalFailed);
        System.out.printf("Total MRs passed: %d%n", totalPassed);

        Map<String, List<TestResult>> byCategory = mrOnly.stream()
                .collect(Collectors.groupingBy(r -> r.mrCategory));

        byCategory.forEach((cat, list) -> {
            long failed  = list.stream().filter(r -> !r.passed).count();
            double rate  = (failed * 100.0) / list.size();
            double avgMs = list.stream().mapToLong(r -> r.durationMs).average().orElse(0);
            System.out.printf("%-14s  detected: %2d/%2d (%.0f%%)  avg: %4.0fms%n",
                    cat, failed, list.size(), rate, avgMs);
        });
    }
}