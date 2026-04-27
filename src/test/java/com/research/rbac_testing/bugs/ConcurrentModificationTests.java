package com.research.rbac_testing.bugs;

import com.research.rbac_testing.helpers.SpringSecurityTestHelpers;
import com.research.rbac_testing.reports.JqwikResultsWriter;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Bug Type: thread safety/concurrency bug
 * Desc: calling setHierarchy() at the same time when another thread calls
 *       getReachableGrantedAuthorities() can cause ConcurrentModificationException
 *       or incorrect results
 *
 * Connection with the Equivalence Category:
 * Equivalence MRs means that two inputs are logically equivalent, no matter how it is expressed.
 * The logical input of these tests is: the hierarchy string and the role questioned does not change
 * between the source and the follow-up.
 *
 * The execution context is what changes: the follow-up adds the concurrent writing.
 * The MR property holds that the output should stay the same since the logical input stays the same.
 *
 * Implementation Note:
 * This class follows the pattern described in Goetz et al. (2006), <i>Java Concurrency in Practice</i>.
 * */

public class ConcurrentModificationTests {

    /**
     * CONC-01: Concurrent same-value writes must not corrupt reads.
     *
     * The test:
     * 5 threads modify the hierarchy (setHierarchy)
     * 5 threads read the hierarchy (getReachableGrantedAuthorities)
     *
     */
    @Test
    void conc01_ConcurrentReadsDuringWrite() throws Exception {
        long startNano = System.nanoTime();
        boolean passed = false;

        try {
            RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
            hierarchy.setHierarchy("ROLE_ADMIN > ROLE_USER");

            // source: set up the expected result in a single-threaded context
            Set<String> sourceResult = SpringSecurityTestHelpers.getReachableAuthorities(hierarchy, "ROLE_ADMIN");

            ExecutorService executor = Executors.newFixedThreadPool(10);
            AtomicInteger violations = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(10);

            // 5 threads for each read and write
            for (int i = 0; i < 10; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            if (threadId < 5) {
                                // writer: re-sets the same logical hierarchy (triggers the internal rebuild)
                                hierarchy.setHierarchy("ROLE_ADMIN > ROLE_USER");
                            } else {
                                // reader (the follow up): results must equal the sourceResult
                                try {
                                    Set<String> followUpResult = SpringSecurityTestHelpers.getReachableAuthorities(hierarchy, "ROLE_ADMIN");
                                    if (!followUpResult.equals(sourceResult)) {
                                        violations.incrementAndGet();
                                    }
                                } catch (ConcurrentModificationException | NullPointerException e) {
                                    violations.incrementAndGet();
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdownNow();

            // MR assertion: every follow-up result must equal the source result
            assertThat(violations.get())
                    .as("Follow up results during concurrent writes should equal to the source result")
                    .isEqualTo(0);

            passed = true;
        } finally {
            long durationUs = (System.nanoTime() - startNano) / 1_000;
            JqwikResultsWriter.write("CONC-01", "EQUIVALENCE", "PRIVILEGE_LEAKAGE|PERMISSION_LOSS", passed, durationUs);
        }
    }

    /**
     * CONC-02: high concurrency reads should not observe corrupted states
     *
     * Source test: two hierarchy configurations are read in a single-thread,
     * which gives a set of valid results
     *
     * Follow-up test: the reader threads will be run at high level of concurrency while
     * the writers will switch between the two hierarchy strings. Every result should
     * either be matching validStateA or validStateB.
     *
     * The results outside those two states mean they have seen a partially built internal map.
     * */
    @Test
    void conc02_HighConcurrencyStressTest() throws Exception {
        long startNano = System.nanoTime();
        boolean passed = false;

        try {
            RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
            hierarchy.setHierarchy("ROLE_ADMIN > ROLE_USER > ROLE_GUEST");

            /* source: pre-computed and valid results single-threaded */
            Set<String> validStateA = SpringSecurityTestHelpers.getReachableAuthorities(hierarchy, "ROLE_ADMIN");

            RoleHierarchyImpl tempHierarchy = new RoleHierarchyImpl();
            tempHierarchy.setHierarchy("ROLE_ADMIN > ROLE_MANAGER > ROLE_USER");
            Set<String> validStateB = SpringSecurityTestHelpers.getReachableAuthorities(tempHierarchy, "ROLE_ADMIN");

            Set<Set<String>> validStates = new HashSet<>(Arrays.asList(validStateA, validStateB));

            int numThreads = 20;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            AtomicInteger violations = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < 50; j++) {
                            if (threadId % 2 == 0) {
                                // writer: should be alternating between two valid hierarchy strings
                                hierarchy.setHierarchy( threadId % 4 == 0
                                        ? "ROLE_ADMIN > ROLE_USER > ROLE_GUEST"
                                        : "ROLE_ADMIN > ROLE_MANAGER > ROLE_USER");
                            } else {
                                // reader (follow-up): the result must be one of the valid states
                                try {
                                    Set<String> followUpResult = SpringSecurityTestHelpers.getReachableAuthorities(
                                            hierarchy, "ROLE_ADMIN");
                                    if (!validStates.contains(followUpResult)) {
                                        violations.incrementAndGet();
                                    }
                                } catch (Exception e) {
                                    violations.incrementAndGet();
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // release all threads at the same time
            doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdownNow();

            // MR assertions: every follow-up read must connect to a valid hierarchy state
            assertThat(violations.get())
                    .as("All follow-up reads should connect to a valid hierarchy state (MR violation count)")
                    .isEqualTo(0);

            passed = true;
        } finally {
            long durationUs = (System.nanoTime() - startNano) / 1_000;
            JqwikResultsWriter.write("CONC-02", "EQUIVALENCE", "PRIVILEGE_LEAKAGE|PERMISSION_LOSS", passed, durationUs);
        }
    }

    /**
     * CONC-03: Result consistency, the collected results must be a subset of the valid states
     *
     * Source Test: two hierarchy configurations are read in a single-thread,
     * which gives a set of valid results
     *
     * Follow-up test: the reader threads will collect all possible results while writers
     * alternate between the two hierarchy strings. Every result should
     * either be matching validStateA or validStateB.
     *
     * Any results outside the produced set means that there's a corrupted states were actually observed.
     *
     * */
    @Test
    void conc03_ConsistentResultsUnderConcurrency() throws Exception {
        long startNano = System.nanoTime();
        boolean passed = false;

        try {
            RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
            hierarchy.setHierarchy("ROLE_ADMIN > ROLE_USER > ROLE_GUEST");

            /* source: pre-compute and valid single-threaded results */
            Set<String> validStateA = SpringSecurityTestHelpers.getReachableAuthorities(hierarchy, "ROLE_ADMIN");

            RoleHierarchyImpl tempHierarchy = new RoleHierarchyImpl();
            tempHierarchy.setHierarchy("ROLE_ADMIN > ROLE_MANAGER > ROLE_USER");
            Set<String> validStateB = SpringSecurityTestHelpers.getReachableAuthorities(tempHierarchy,"ROLE_ADMIN");

            Set<Set<String>> validStates = new HashSet<>(Arrays.asList(validStateA, validStateB));

            ExecutorService executor = Executors.newFixedThreadPool(8);
            Set<Set<String>> observedResults = Collections.synchronizedSet(new HashSet<>());
            CountDownLatch latch = new CountDownLatch(8);

            for (int i = 0; i < 8; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            if (threadId < 3) {
                                // writer threads: alternate between the two valid hierarchy strings
                                hierarchy.setHierarchy(
                                        threadId % 2 == 0
                                                ? "ROLE_ADMIN > ROLE_USER > ROLE_GUEST"
                                                : "ROLE_ADMIN > ROLE_MANAGER > ROLE_USER"
                                );
                            } else {
                                // reader threads (follow-up): collect all the observed results
                                try {
                                    Set<String> result = SpringSecurityTestHelpers.getReachableAuthorities(
                                            hierarchy, "ROLE_ADMIN");
                                    observedResults.add(result);
                                } catch (Exception ignored) {
                                    /* the focus is to inspect which result values were observed*/
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdownNow();

            /* MR: every collected result should relate to a valid hierarchy state */
            Set<Set<String>> unexpectedResults = new HashSet<>(observedResults);
            unexpectedResults.removeAll(validStates);

            assertThat(unexpectedResults)
                    .as("All observed results must correspond to a valid hierarchy state. "
                            + "Unexpected results: %s" + unexpectedResults)
                    .isEmpty();

            passed = true;
        } finally {
            long durationUs = (System.nanoTime() - startNano) / 1_000;
            JqwikResultsWriter.write("CONC-03", "EQUIVALENCE", "PRIVILEGE_LEAKAGE|CONSTRAINT_VIOLATION", passed, durationUs);
        }
    }

    /**
     * CONC-04: repeated execution to raise race condition exposure
     *
     * source test: a single-thread baseline
     *
     * follow-up: 2 out of 6 threads will try to re-write the same hierarchy
     * while the remaining 4 threads will read the ROLE_ADMIN repeatedly.
     * Every single thread that was reproduced by the follow-up test should
     * match the baseline thread.
     * */
    @RepeatedTest(5)
    void conc04_RepeatedConcurrentTest(RepetitionInfo repetitionInfo) throws Exception {
        long startNano = System.nanoTime();
        boolean passed = false;

        // unique ID per repetition so every row lands in the CSV
        String mrId = "CONC-04-R" + repetitionInfo.getCurrentRepetition();

        try {
            RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
            hierarchy.setHierarchy("ROLE_ADMIN > ROLE_USER");

            /* source: a single thread baseline */
            Set<String> sourceResult = SpringSecurityTestHelpers.getReachableAuthorities(hierarchy, "ROLE_ADMIN");

            ExecutorService executor = Executors.newFixedThreadPool(6);
            AtomicInteger violations = new AtomicInteger(0);

            for (int i = 0; i < 6; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    for (int j = 0; j < 50; j++) {
                        if (threadId % 3 == 0) {
                            // writer: re-set the same logical hierarchy
                            hierarchy.setHierarchy("ROLE_ADMIN > ROLE_USER");
                        } else {
                            // reader: the results should equal the sourceResult
                            try {
                                Set<String> followUpResult = SpringSecurityTestHelpers.getReachableAuthorities(hierarchy, "ROLE_ADMIN");
                                if (!followUpResult.equals(sourceResult)) {
                                    violations.incrementAndGet();
                                }
                            } catch (Exception e) {
                                violations.incrementAndGet();
                            }
                        }
                    }
                });
            }

            executor.shutdown();
            boolean finished = executor.awaitTermination(5, TimeUnit.SECONDS);
            assertThat(finished)
                    .as("Executor did not terminate within the time limit")
                    .isTrue();

            // MR: every follow-up result should equal the source result
            assertThat(violations.get())
                    .as("Repeated concurrent reads must consistently equal the source result (MR violation count)")
                    .isEqualTo(0);

            passed = true;
        } finally {
            long durationUs = (System.nanoTime() - startNano) / 1_000;
            JqwikResultsWriter.write(mrId, "EQUIVALENCE", "PRIVILEGE_LEAKAGE|PERMISSION_LOSS", passed, durationUs);
        }
    }

    /**
     * CONC-05: Rapid concurrent writes should not produce partial or empty reads
     *
     * source: a known query which always be true; query a role must always return a set containing that role itself,
     * the results must not be empty
     *
     * follow-up: one writer thread resets the same hierarchy string 200 times while one reader thread performs 400 concurrent queries
     * Each of the queries is a follow-up test case.
     *
     * */
    @Test
    void conc05_RapidModificationDuringQuery() throws Exception {
        long startNano = System.nanoTime();
        boolean passed = false;

        try {
            RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
            hierarchy.setHierarchy("ROLE_ADMIN > ROLE_MANAGER > ROLE_USER");

            // source: set the expected result single-threaded
            Set<String> sourceResult = SpringSecurityTestHelpers.getReachableAuthorities(hierarchy, "ROLE_ADMIN");

            AtomicInteger violations = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(2);

            // follow-up: rapidly re-sets the same logical hierarchy
            Thread writer = new Thread(() -> {
                try {
                    for (int i = 0; i < 200; i++) {
                        hierarchy.setHierarchy("ROLE_ADMIN > ROLE_MANAGER > ROLE_USER");
                    }
                } finally {
                    latch.countDown();
                }
            });

            // follow-up reader: queries concurrently and compares against sourceResult
            Thread reader = new Thread(() -> {
                try {
                    for (int i = 0; i < 400; i++) {
                        try {
                            Set<String> followUpResult = SpringSecurityTestHelpers.getReachableAuthorities(
                                    hierarchy, "ROLE_ADMIN");
                            if (!followUpResult.equals(sourceResult)) {
                                violations.incrementAndGet();
                            }
                        } catch (ConcurrentModificationException | NullPointerException e) {
                            violations.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });

            writer.start();
            reader.start();
            latch.await(10, TimeUnit.SECONDS);

            // MR: every follow-up read must equal the source result
            assertThat(violations.get())
                    .as("Follow-up reads during rapid writes must equal the source result. "
                            + "Violations detected: %d", violations.get())
                    .isEqualTo(0);

            passed = true;
        } finally {
            long durationUs = (System.nanoTime() - startNano) / 1_000;
            JqwikResultsWriter.write("CONC-05", "EQUIVALENCE", "PERMISSION_LOSS|CONSTRAINT_VIOLATION", passed, durationUs);
        }
    }
}
