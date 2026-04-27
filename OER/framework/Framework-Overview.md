# Framework Overview
This file explains the structure of the framework, the usage of each Java file, and how they are connected to each other. While it is not necessary to read the source code to follow along, however, there are links to the relevant files if you would like to understand the content more deeply.

## The Big Picture
The framework tests one class within Spring Security's system, which is the RoleHierarchyImpl class. The class is responsible for managing role inheritance in Java web applications. For example, there's a hierarchy with `ROLE_ADMIN > ROLE_USER > ROLE_GUEST`, the class will determine which roles each user can access. 

The main point of the framework is
to check whether RoleHierarchyImpl behaves correctly across a variety of inputs using MRs
(Metamorphic Relations).
For more in-depth information about MRs, you can refer to the tutorial/01-What-Is-MT.md file.
Across the three main categories and the concurrency extension, the framework contains 35 tests in total [30 MRs and HMRs across the main categories, and 5 concurrency tests (CONC-01 to CONC-05)]. The results CSV shows 39 rows, as CONC-04 runs 5 times via `@RepeatedTest(5)`.

| Category     | ID prefix | MRs | HMRs |
|--------------|-----------|-----|------|
| Inheritance  | INH       | 8   | 2    |
| Monotonicity | MON       | 7   | 3    |
| Equivalence  | EQ        | 5   | 5    |
| Concurrency  | CONC      | 5   | —    |

> CONC-04 runs 5 times via @RepeatedTest(5), so the results CSV shows 9 concurrency rows in total

The difference between MRs and HMRs is that any fault raised in an MR is a definitive fault, while a violation in an HMR may reflect in an unexpected behavior within the system, so it is not a confirmed bug. It could be a design decision, an edge case, or some limitation to the test itself. You can easily distinguish which one is an MR and an HMR in the source code since it's labelled in the code comments and in the description field of `@BugType`.

---

## Source files

```
src/
└── test/java/com/research/rbac_testing/
    ├── metamorphic/
    │   ├── InheritanceBasedMRs.java      ← INH-01 through INH-10
    │   ├── MonotonicityBasedMRs.java     ← MON-01 through MON-10
    │   └── EquivalenceBasedMRs.java      ← EQ-01 through EQ-10
    ├── helpers/
    │   └── SpringSecurityTestHelpers.java ← shared utilities
    ├── annotations/
    │   └── BugType.java                  ← @BugType annotation
    ├── reports/
    │   ├── JqwikResultsWriter.java       ← writes CSV output
    │   ├── MRMetrics.java                ← timing + pass/fail
    │   ├── TestReport.java               ← summary
    │   └── TestResult.java               ← per-MR record
    └── RBACMetamorphicTestSuite.java     ← entry point for the full suite
```

---

## What Each Layer Does:

### MR Test Classes

Each category has its own class. Every test within the class is a `jqwik` property, meaning jqwik will generate the test 150 times with different random inputs by default. Each test has the same two-step structure:

1. **Source test case:** sets up a role hierarchy and record the output from RoleHierarchyImpl class. 
2. **Follow-up test case:** apply a transformation (e.g., adding more roles, re-order the hierarchy, extending the chain, etc) and checks whether the outputs satisfy the expected relationship.

If one of the assertions fails, jqwik will report a violation, which can be seen in the terminal output.

### SpringSecurityTestHelpers

This is a utility class that wraps the parts of RoleHierarchyImpl so the methods can be used over and over:
- `buildHierarchyString(map):` this class converts `Map<String, List<String>>` to `ROLE_A > ROLE_B` format which expected by `setHierarchy`. 
- `getReachableAuthorities(impl, role):` returns the set of all reachable from a given role. 
- `getInternalRoleMap(impl):` uses reflection (a Java feature) to inspect the internal one-step map of Spring Security. 
- `areHierarchiesEquivalent(impl1, impl2, roles):` compares the two hierarchies across a list of test roles. 
- `calculateDepth(map):` calculates the hierarchy depth using BFS
- `hasCycle(map):` detects any cycles using DFS method

### @BugType annotation:

Every `@Property` method is annotated with `@BugType`, which records:
- `mrId`: the identifier of each MR 
- `category`: INHERITANCE, MONOTONICITY, or EQUIVALENCE 
- `bugTypes`: the bug type which the MR targets (e.g., `PERMISSION_LOSS`, `CYCLIC_DEPENDENCY`)
- `description`: explanation of the the relation 

The data is read during runtime by the reporting pipeline.

### Reporting Pipeline:
- `BeforeProperty`: starts a timer before each test. 
- `AfterProperty`: reads the @BugType annotation, records its duration, whether the test pass or fail, and calls the `JqwikResultsWriter.write(...)`. 
- `JqwikResultsWriter.write`: this method writes three CSV files to the target/mr-results/ after each test run is finished.

The three output files are:

| File | Contents |
|------|----------|
| `jqwik_results_*.csv` | Raw jqwik property-level results |
| `raw_results_*.csv` | Per-MR execution data (timing, pass/fail, bug types) |
| `summary_*.csv` | Aggregated results across all 39 MRs |


## Known Bugs Found

So far, the framework has detected two MRs that failed on the current version of Spring Security:
- **INH-02 (antisymmetry check):** the RoleHierarchyImpl does not correctly reject cyclic hierarchies in all cases
- **EQ-04 (consistency check):** the equivalent hierarchy produce different reachability results under certain conditions.

If you have noticed, there is a separate bug folder within the project and underneath the folder,
there's a java file called `ConcurrentModificationTests.java`.
This file consists of concurrency tests which expose some thread-safety issues within RoleHierarchyImpl.
We have managed to find the root cause of the issues which is:
the `setHierarchy()` builds two internal maps when it is called.
This leaves a window where the concurrent readers might see a mismatched internal state.

---

*Licence: CC BY 4.0. Code snippets: MIT.*


