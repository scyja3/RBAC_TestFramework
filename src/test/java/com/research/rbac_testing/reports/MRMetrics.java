package com.research.rbac_testing.reports;

/*
* This file is a JUnit extension which collects data automatically
* for every @BugType-annotated tests.
*
* the results will be forwarded to TestResultsRegistry
* */

import com.research.rbac_testing.annotations.BugType;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MRMetrics
        implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final String START_TIME_KEY = "start_time";

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        context.getStore(ExtensionContext.Namespace.GLOBAL)
                .put(START_TIME_KEY, System.nanoTime());
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        long startNano = context.getStore(ExtensionContext.Namespace.GLOBAL)
                                .get(START_TIME_KEY, Long.class);
        long durationMs = (System.nanoTime() - startNano) / 1_000_000;

        // gets the Java method
        Method testMethod = context.getRequiredTestMethod();
        BugType annotation = testMethod.getAnnotation(BugType.class);

        if (annotation == null) return;

        // checks if the test failed or passed
        boolean passed = !context.getExecutionException().isPresent();

        String bugTypeCsv = Arrays.stream(annotation.bugTypes())
                                    .map(Enum::name)
                .collect(Collectors.joining("|"));

        TestReport.getInstance().record(
                new TestResult(
                        annotation.mrId(),              // e.g. "INH-01"
                        annotation.category().name(),   // e.g. "INHERITANCE"
                        bugTypeCsv,                     // e.g. "PERMISSION_LOSS"
                        testMethod.getName(),           // e.g. "seniorRoleHasAllJuniorPermissions"
                        passed,                         // true or false
                        durationMs,                     // e.g. 12
                        passed ? "" : context.getExecutionException()
                                .map(Throwable::getMessage)
                                .orElse("unknown failure")
                )
        );
    }
}
