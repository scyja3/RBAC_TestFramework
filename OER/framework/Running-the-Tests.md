# Running the Tests

This file explains how to run the framework and how to analyse the output. Don't worry if you have no prior experience in jqwik or Spring Security, this file will explain everything in details.

---
### Prerequisites
Before running anything, make sure you have the following versions:
- Java 17 or later (to check which version you have: java -version)
- Maven 3.8 or later (check with mvn -version)

For Spring Security, jqwik, and AssertJ, all of these are downloaded automatically by Maven when you run the tests.

---
### Running the full Suite

The command to clone the repository and run:
```bash
git clone <repo-url>
cd rbac-testing
mvn test
```
After running those commands above, Maven will compile the project, run all of the 39 MRs with the concurrency tests, and write the results to target/mr-results/. Running the file for the first time will take some time (1-2 minutes) since Maven needs to download the dependencies.

---
### What you will see in the terminal:

`jqwik` will print a summary for each `@Property` as it runs, and if the property pass, it will look like:
```
               |-------- onSuccess --------|
InheritanceBasedMRs:transitiveClosureInChain =
tries = 150          | # of calls to property
checks = 150         | # not rejected by assumptions
generation = RANDOMIZED
```

If the property failed, then the jqwik will show the input that failed and attempts to shrink it:

```
|-------- onFailure --------|
InheritanceBasedMRs:antisymmetryPreventsSimpleCycles =
tries = 1
checks = 1
...
Shrunk Sample
...
AssertionError: expected CycleInRoleHierarchyException but nothing was thrown
```

This means that jqwik has found an input which violates the relation, and the "shrunk" sample shows the simplest version of that input. This makes it easier to understand the reason what went wrong.

---
### Reading the CSV output

After running the framework, there will be three files that appear in the `target/mr-results/`.

Within the `raw_results_*.csv`, there will be one row per MR execution.

| Column | Meaning |
|--------|---------|
| mr_id | The MR identifier (e.g., INH-01) |
| category | INHERITANCE, MONOTONICITY, or EQUIVALENCE |
| bug_types | Bug types targeted, pipe-separated (e.g., PERMISSION_LOSS\|PRIVILEGE_LEAKAGE) |
| passed | true or false |
| duration_ms | How long the property took to run in milliseconds |

`summary_*.csv`

One row per MR, aggregated. This is useful for comparing categories at one glance, includes the passing rate, average duration, and bug type coverage.

`jqwik_results_*.csv`

Raw jqwik property-level data. This is lower-level than the other two files and mainly useful if you want to dig into how many tries were attempted or how many inputs were rejected.

---
### Running the Files Per Category

You can also run the files per one category using Maven's -Dtest flag:

```bash
# Run only Inheritance-Based MRs
mvn test -Dtest=InheritanceBasedMRs

# Run only Monotonicity-Based MRs
mvn test -Dtest=MonotonicityBasedMRs

# Run only Equivalence-Based MRs
mvn test -Dtest=EquivalenceBasedMRs
```

You can also run a single MR since jqwik supports running a single property by using its method name:
```bash
mvn test -Dtest="InheritanceBasedMRs#transitiveClosureInChain"
```

This is useful when you are investigating a specific failure or writing a new MR.

Reproducing a known failure is also possible. There are two MRs that failed deterministically (INH-02 and EQ-04). You can reproduce them by running:

```bash 
mvn test -Dtest="InheritanceBasedMRs#antisymmetryPreventsSimpleCycles"
mvn test -Dtest="EquivalenceBasedMRs#representationFormatEquivalence"
```
The output will confirm the failure and show you the exact input that triggered it.

---
### Troubleshooting

If you run into any of these problem, here are the solutions:
- `mvn test:` fails with no compile error: you may not have Java version 17 or later. This caused an error since Spring Security 6.x does not support older versions. 
- You see no files in `target/mr-results/`: The folder is only created after the tests run, so if the build failed before any tests ran, the folder will be empty or missing. Try to check the Maven output for compile errors first. 
- A test that was passing now fails: This is possible if you changed theRoleHierarchyImpl class or the hierarchy string format. Try to check whether your change affects how `setHierarchy()` parses input.

---
Licence: CC BY 4.0. Code snippets: MIT.