# Metamorphic Relation (MR) Categories

## Why use Three Categories?

This project mainly focuses on exploring which of the three categories—inheritance,
monotonicity, and equivalence—is the most effective in detecting bugs within the role hierarchy.
With the MT implementation,
we've managed to make a total of 35 MRs across all three categories and concurrency extension
(39 rows in the results CSV, as CONC-04 runs 5 times) [1].
Each category also aims for different bugs, and they act as a lens that points to the same system.
One category might miss the bugs that are only catchable through the other category.
For example,
some faults that are detected when re-initialising the hierarchy won't be detected by the inheritance category
since it only checks if the graph structure is correct or not.
However,
this fault might be detected by the equivalence category
since it checks whether two descriptions of the same hierarchy will behave the same way.
Therefore, the three categories will be better applied together.

The categorisation of the MRs follows a framework, as described by Ying et al. (2025) [2]:

## The Inheritance Category (INH-01 to INH-10)
This category checks the correctness of the entire hierarchy structure. It treats the role hierarchy as a directed graph and explores if the graph has the characteristics of the mathematical rules that it should've followed [3]. The main properties of this category are the following:

- **Transitivity** is when a role, A, gets permissions from role, B, and B gets permissions from role, C. This means that role A also receives permissions from role C, but not directly; it acquires them from role C through role B (INH-01, INH-06).

- **Antisymmetry** means that if role A inherits permissions from role B, then role B should not inherit them from role A; otherwise, it creates a loop, which is not possible within a hierarchy (INH-02).

- **Multiple paths** mean that if two paths exist from one role to another, removing one path shouldn't affect the other path (INH-03).

- **Sibling independence** means that modifying one branch within the hierarchy should not affect the other separate branches (INH-09).

This category mainly targets the **permission loss**, **cyclic dependency**, and **privilege leakage bugs**.
Further information about each of the MRs are in the table below:

| MR ID  | MR Name                          | Input Transformation                                                                                                                             | Output Relation                                                                                                                                             |
|--------|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| INH-01 | Transitive Closure Property      | A simple chain hierarchy of varying depth (3–8 levels) where each role has exactly one child                                                     | Top role must transitively reach all roles in the chain                                                                                                     |
| INH-02 | Antisymmetry Property            | Start with `ROLE_SENIOR` > `ROLE_JUNIOR`, then add reverse edge `ROLE_JUNIOR` > `ROLE_SENIOR` to create a cycle                                  | Setting a cyclic hierarchy must throw `CycleInRoleHierarchyException`; the non-cyclic hierarchy must have the senior role containing all junior permissions |
| INH-03 | Path Independence Property       | Create diamond hierarchy (A reaches D via both B and C), then remove one path (A > C > D)                                                        | A must still reach D through the remaining path; A should not reach C once that path is removed                                                             |
| INH-04 | Hierarchy Extension Property     | Add new roles to the bottom of the existing hierarchy                                                                                            | All original roles must retain their existing reachability sets                                                                                             |
| INH-05 | Role Removal Impact Property     | Remove one child role (C) from a parent with multiple children (B has C and D)                                                                   | Parent must still reach unaffected child (D); parent should no longer reach removed child (C); exactly one permission lost                                  |
| INH-06 | Multi-Hop Permission Propagation | A simple chain hierarchy of varying depth (3–6 levels) where each role has exactly one child                                                     | Top role must transitively reach all permissions in the chain; middle-tier role must contain all bottom-tier permissions                                    |
| INH-07 | Diamond Inheritance Consistency  | Create diamond pattern (A reaches D via both B and C), then compare to linear chain (A > B > D)                                                  | Diamond pattern should have all four distinct roles with no duplications; both structures should reach the bottom role                                      |
| INH-08 | Permission Addition Propagation  | Start with hierarchy A > B, then add a new role beneath the junior role (B > NEW_PERMISSION)                                                     | Senior role should gain the new permission and have exactly one more permission than before                                                                 |
| INH-09 | Sibling Role Independence        | Create two independent chains (Chain A and Chain B), then modify only Chain B by adding an extra role                                            | Chain A permissions must stay the same and must not gain any permissions from Chain B's modifications                                                       |
| INH-10 | Inheritance Path Symmetry        | Create a role with two parent paths to the same permission (TARGET has PATH1 and PATH2, both leading to SHARED_PERMISSION), then remove one path | Shared permission must exist through both dual and single paths; permission should appear exactly once despite multiple paths                               |

## The Monotonicity Category (MON-01 to MON-10)

This category asks if adding more roles only will increase the role count and never reduce it. This is essentially the monotonicity property, where it will only make sense; adding will always increase and never reduce [2].
- **upward monotonicity:** a parent role should have at least the same, if not more, permissions as any of its children (MON-01).
- **addition monotonicity:** adding a new permission will only increase the permission count (MON-02).
- **depth monotonicity:** extending the hierarchy downwards (adding more roles) should never decrease what the previous roles can reach (MON-08).
- **widening monotonicity:** adding more child roles to the hierarchy should not affect any of the permissions from the existing roles (MON-06).
- **cross-branch monotonicity:** extending one branch should not have any effects on the other separate branches (MON-10).

This category mainly targets permission loss and privilege leakage bugs.

>Note: across all 150 tries on each MR, there are no bugs detected. This does not mean that it is not useful; it suggests that Spring Security's permission propagation logic is correctly implemented.

| MR ID  | MR Name                                    | Input Transformation                                                                                                   | Output Relation                                                                                                       |
|--------|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| MON-01 | Upward Monotonicity Property               | Build a multi-level chain (2–6 levels) and check each parent-child pair                                                | Each parent role must have all permissions of its immediate child role                                                |
| MON-02 | Permission Addition Monotonicity           | Start with two separate chains (A > B and C > D), then connect them (B > C)                                            | No role should lose permissions; A and B must gain access to C and D                                                  |
| MON-03 | Hierarchy Strengthening Property           | Start with a sparse hierarchy (minimal connections), then add cross-connections to create a dense hierarchy            | Dense hierarchy reachability count must be greater than or equal to sparse; top role must reach all roles             |
| MON-04 | Hierarchy Weakening Property               | Start with a dense hierarchy (A reaches B, C, D directly), then remove edges to create a sparse hierarchy (A > B only) | Sparse reachability must be a subset of dense; roles not directly connected should no longer be reachable             |
| MON-05 | Subset Consistency Across Hierarchy Levels | Build a deep chain hierarchy (4–7 levels) and check all adjacent level pairs                                           | For any senior-junior pair, senior reachability must contain all junior authorities; top role must reach all roles    |
| MON-06 | Role Removal Constraint                    | A full chain hierarchy (A > B > C > D), then remove middle role C (resulting in A > B > D)                             | No role should gain any permission; permission counts should only decrease or remain the same                         |
| MON-07 | Permission Revocation Monotonicity         | A hierarchy with multiple permissions (ADMIN > USER, ADMIN > AUDIT), then remove one permission (remove AUDIT)         | Total system-wide permissions should decrease; ADMIN should no longer reach the revoked permission                    |
| MON-08 | Hierarchy Depth Monotonicity               | Start with a shallow hierarchy (2–4 levels), then add additional depth (1–3 more levels)                               | Top role must maintain all original permissions and gain exactly the number of newly added permissions                |
| MON-09 | User Assignment Monotonicity               | Assign a user to a single role (BASIC > READ), then assign to an additional role (EXTRA > WRITE)                       | Combined permissions should equal the union of both assignments; permission loss must not occur                       |
| MON-10 | Cumulative Permission Addition             | Add permissions sequentially one by one, then compare to batch addition                                                | Both methods must produce the same final state; each sequential step must monotonically increase the permission count |

## The Equivalence Category (EQ-01 to EQ-10 and CONC-01 to CONC-05)
This category will examine whether, by describing a hierarchy differently, it will create a different output. Regardless of how a hierarchy is constructed (i.e., different order, format, or naming), it should be creating the same results [2]. These are the main properties of this category:
- declaration order independence: for example, creating ROLE A > ROLE B before ROLE B > ROLE C and the reverse order shouldn't have any difference (EQ-01).
- representation format equivalence: defining the same hierarchy with
  different whitespace or formatting (e.g. spaces around `>`, extra newlines)
  should produce identical reachability results (EQ-04).
> Note: This MR detected a fault where `RoleHierarchyImpl` parsed two
formatting variations differently, causing the same hierarchy to return
an inconsistent results depending on how the string was written.
- edge permutation invariance: shuffling the order of the edges within the hierarchy string shouldn't have any effect on the outcome (EQ-05).
- flattening equivalence: there is a term called '**deep chain hierarchy**', which means ROLE_A is connected to ROLE_B, ROLE_B is connected to ROLE_C, and ROLE_C is connected to ROLE_D. This deep chain must have the same output as the '**flattened hierarchy**' where ROLE_A directly inherits from ROLE_B, ROLE_C, and ROLE_D (EQ-10).

This category mainly aims for **permission loss** and **privilege leakage bugs**.

| MR ID | MR Name                           | Input Transformation                                                                                                                                                                  | Output Relation                                                                                                                                     |
|-------|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| EQ-01 | Declaration Order Independence    | Define the same hierarchy edges in different declaration orders                                                                                                                       | All roles must have identical reachability regardless of edge declaration order                                                                     |
| EQ-02 | Transitive Redundancy Equivalence | Start with a minimal chain (only adjacent edges), then add an explicit transitive edge from top to bottom                                                                             | Both hierarchies must produce identical reachability for all roles                                                                                  |
| EQ-03 | Name Invariance Property          | Create a structurally identical hierarchy with different role names (A/B/C/D renamed to X/Y/Z/W)                                                                                      | Both hierarchies must have the same depth and structurally equivalent reachability patterns                                                         |
| EQ-04 | Representation Format Equivalence | Define the same hierarchy with different whitespace and formatting (spaces, newlines, etc.)                                                                                           | All formatting variations must produce identical reachability results                                                                               |
| EQ-05 | Edge Permutation Invariance       | Define the same set of edges in multiple random permutations                                                                                                                          | All permutations must produce identical reachability for every role                                                                                 |
| EQ-06 | Role Renaming Invariance          | Create a hierarchy with original role names (MANAGER > EMPLOYEE > INTERN), then rename all roles (SUPERVISOR > WORKER > TRAINEE)                                                      | Renamed hierarchy must have identical structural depth; top roles must reach the same number of authorities                                         |
| EQ-07 | Hierarchy Isomorphism             | Create two structurally isomorphic hierarchies with different role names (H1: ROLE_H1_A > (ROLE_H1_B, ROLE_H1_C), where ROLE_H1_B > ROLE_H1_D and ROLE_H1_C > ROLE_H1_E; H2: same fork structure with ROLE_H2_ prefix | Corresponding roles must have the same number of authorities and equivalent structural properties (depth, width)                                    |
| EQ-08 | Permission Set Commutativity      | Assign permissions in different orders                                                                                                                                                | All orderings must produce the same permission sets; total permission count must be identical across all orderings                                  |
| EQ-09 | Alternative Path Equivalence      | Create a permission reachable through two paths (TOP > PATH_A > TARGET and TOP > PATH_B > TARGET), then test with a single path (TOP > PATH_A > TARGET)                               | Both configurations must reach TARGET                                                                                                               |
| EQ-10 | Hierarchy Flattening Equivalence  | Start with a deep nested hierarchy (A > B > C > D), then create a flattened version (A > B, C, D)                                                                                     | Both hierarchies must allow the top role to reach all the same bottom roles; flattened and deep versions must have identical top-level reachability |

### The concurrency MRs as an extension of the Equivalence (CONC-01 to CONC-05)
If you can see in the known bugs file, there's a **ConcurrentModificationTests** class,
which is an extension of the equivalence category in the execution context.
The main MR used for this class is
that the `getReachableGrantedAuthorities()` method should return consistent results
whether or not the thread is calling the `setHierarchy()` simultaneously.
`setHierarchy()` is the method when you want to define a role hierarchy;
meanwhile, `getReachableGrantedAuthorities()` is the method that should be called after defining the role hierarchy.
In other words, even though there are two calls that ask the same question,
the system should be giving the users the same answer even if they have a concurrent load.
This is related to equivalence since it checks for the output consistency across different execution scenarios.

The faults raised from the tests in this file are considered a real bug instead of a design flaw. This is because the RoleHierarchyImpl class uses an unsynchronised HashMap. Based on Java's official documentation [4], the concurrent modification of a HashMap by multiple threads does produce some undefined behaviours.

> Note: the concurrency tests are developed outside the equivalence file since it wasn't originally part of the equivalence category. Initially, it was written to check whether Spring Security can handle multiple threads safely. However, during development, the characteristics of these tests does match with the equivalence properties.
>
| Test ID | Test Name                            | What It Does                                                                                                                  | Expected Behaviour                                                                                                                                                |
|---------|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| CONC-01 | Concurrent Reads During Write        | 5 writer threads and 5 reader threads operate simultaneously on the same `RoleHierarchyImpl` instance for 100 iterations each | No `ConcurrentModificationException` should be thrown; exception count must remain at zero                                                                        |
| CONC-02 | High Concurrency Stress Test         | 20 threads start simultaneously — even-numbered threads write, odd-numbered threads read — each running 50 iterations         | No exceptions of any kind should be thrown across all 20 threads                                                                                                  |
| CONC-03 | Consistent Results Under Concurrency | 3 writer threads alternate between two known hierarchy strings; 5 reader threads record every distinct result they observe    | At most 3 distinct results should be observed — the two valid hierarchy states plus the role itself; any additional results indicate non-deterministic corruption |
| CONC-04 | Repeated Concurrent Access           | 6 threads run concurrently — every third thread writes, the rest read — repeated 5 times via `@RepeatedTest(5)`               | No exceptions should occur across any of the 5 repetitions; recorded as CONC-04-R1 through CONC-04-R5 in the results CSV                                          |
| CONC-05 | Rapid Modification During Query      | One writer thread performs 200 rapid `setHierarchy()` calls while one reader thread performs 400 simultaneous queries         | No `ConcurrentModificationException` or `NullPointerException` should be thrown; no empty or incomplete result sets should be returned                            |

---

*Licence: CC BY 4.0. Code snippets: MIT.*

---

## References

[1] Spring Security `RoleHierarchyImpl` source code, Spring Security 6.2.0. https://github.com/spring-projects/spring-security

[2] Z. Ying, D. Towey, et al., "Hypothesised Metamorphic Relations," 2025. *(Framework used for MR/HMR classification in this project.)*

[3] American National Standards Institute / International Committee for Information Technology Standards, "Role Based Access Control," ANSI INCITS 359-2004, 2004.

[4] Oracle, "HashMap," Java SE Documentation. https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html. 
