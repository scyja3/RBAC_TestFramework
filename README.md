# Testing the Untestable: Computer Security (Metamorphic Testing for Role-Based Access Control)

> Author: Julienne Adwin (scyja3@nottingham.edu.cn) \
> Licence: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) — free to use, adapt, and share with attribution  
> Code licence: [MIT](https://opensource.org/licenses/MIT)  

## What Is It?
This repository is a **learning resource** and a **research framework**. The research aspect involves the implementation of testing a methodology known as **Metamorphic Testing (MT)** to find real bugs in Spring Security's role hierarchy implementation. The OER aspect is for people who want to understand how MT works, how it is useful for security systems, and how to apply it themselves. 

This README will introduce metamorphic testing if you have never heard of it before. However, if you do know the basics of Metamorphic Testing, are familiar with what MRs are, and just want to see how the framework works, you can jump straight to the [Framework Guide](oer/framework-guide/).

## Who is this for?
This OER is written for two audiences:
- **Computer Science students that are new to metamorphic testing**: the tutorial section will explain everything from scratch, with worked examples before introducing any code
- **Students who already have some basic knowledge in MT**: can skip ahead to the MR category breakdowns and the framework walkthrough (go further into the specific design decisions made and why). 

No prior knowledge of Spring Security is needed; however, some basic understanding of Java and access control concepts will help. 

## Repository Structure
```
rbac-testing/
├── oer/
│   ├── README.md                   ← you are here
│   ├── tutorial/
│   │   ├── 01-What-Is-MT.md        ← the basics of metamorphic testing
│   │   ├── 02-What-Is-RBAC.md      ← the basics of Role-Based Access Control
│   │   ├── 03-MR-categories.md     ← the three MR categories used in this project
│   │   └── 04-worked-examples.md   ← concrete examples of each MR type
│   └── framework-guide/
│       ├── overview.md             ← how the framework is structured
│       └── running-the-tests.md    ← how to run everything and read the output
└── target/
    └── mr-results/                 ← generated after running mvn test
        ├── jqwik_results_*.csv     ← raw jqwik property test recordings
        ├── raw_results_*.csv       ← full per-MR execution data
        └── summary_*.csv           ← aggregated results across all 35 MRs
```

> **Note:** The `target/` directory is excluded from version control by default.  
> To reproduce the results, clone the repo and run `mvn test`. Output CSVs will  
> be generated automatically in `target/mr-results/`.

## How to Run The Tests 
If you only want to see the test results, run these commands:
```bash
git clone <repo-url>
cd rbac-testing
mvn test
```
Results will be written to `target/mr-results/`. There will be two main CSV files: `summary_*.csv` (answers the research questions) and a `raw_results_*.csv` (full details per MR).

**Prerequisites:** Java 17+, Maven 3.8+

## What do the tests actually do?
This project has 30 **metamorphic relations**, 10 for each category (each category targets a different property of role hierarchies):
### Inheritance-Based MRs (INH-01 to INH-10)
This category checks if the **structure** of the hierarchy is correct.
> *Example:* If ROLE_ADMIN > ROLE_MANAGER > ROLE_USER, then ROLE_ADMIN must be able to access ROLE_USER even without an direct edge. This relationship is called transitivity, and INH-01 checks that it holds at every chain length. 

### Monotonicity-Based MRs (MON-01 to MON-10)
This category checks the **direction of change** of a hierarchy, which means that adding roles or permissions can only increase what a role can reach, but never decrease it. 
> *Example*: Adding a new child role to an existing hierarchy, every other role that already has access to a given permission should still have the same access; otherwise, it will raise a fault. MON-02 checks for these inheritance edges. 

### Equivalence-Based MRs (EQ-01 to EQ-10)
This category checks how **describing** a role hierarchy doesn't change what it does (e.g. different orderings, formats, or representations of the same structure). 
> *Example*: EQ-01 checks whether `ROLE_A > ROLE_B \n ROLE_B > ROLE_C` in that order vs. `ROLE_B > ROLE_C \n ROLE_A > ROLE_B` in the reverse order should produce the same reachability results.

## What is the difference between MR and HMR?
Not all of the 30 MRs have the same relationship. This classification follows Ying et al.'s framework:

- **MRs (Metamorphic Relations)** — 21 of the 30 — encode properties that must *always* hold.
- **HMRs (Hypothetical MRs)** — 9 of the 30 — encode properties that are *reasonable to expect* but not formally guaranteed. A violation might be a bug, or it might be an intentional design decision that the documentation doesn't clearly cover.

This tutorial will explain this in more detail, since when interpreting the results, it will matter a lot. 

## Research Questions 
The project aims to address the following questions:

| Question | Short form |
|---|---|
| **Main RQ** | Which MR category (Inheritance / Monotonicity / Equivalence) is most effective at finding bugs in role hierarchy implementations? |
| **SRQ1** | How does each category perform across different bug types (privilege leakage, permission loss, cyclic dependencies, constraint violations)? |
| **SRQ2** | Do the three categories differ significantly in computational cost? |
| **SRQ3** | Does detection effectiveness change as hierarchy complexity increases (depth, width, number of paths)? |

The `mr-results/` folder contains the data that answers these questions. The tutorial section walks through how to interpret it.

## How to cite this

If you use or adapt this material:

```
Julienne (2026). Testing the Untestable: Metamorphic Testing for Role-Based Access Control.
University of Nottingham Ningbo China.
Licensed under CC BY 4.0. https://creativecommons.org/licenses/by/4.0/
```

---

## Acknowledgements

Supervised by Dr. Dave Towey, University of Nottingham Ningbo China.  
Built using [Spring Security](https://spring.io/projects/spring-security), [jqwik](https://jqwik.net/), and [AssertJ](https://assertj.github.io/doc/).  
MR classification informed by Ying et al. (2025) and Chen et al.'s foundational MT survey.
