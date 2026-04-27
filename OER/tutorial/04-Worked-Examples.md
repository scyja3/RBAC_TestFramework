# Worked Examples
This file will explain one example from each MR category. Each of the examples will have the same structure, which includes: the property being tested, its source and follow-up inputs, the relation that must be held, and what a violation would look like.

Overview: Each MR in this project is designed to test the RoleHierarchyImpl class within the Spring Security system. Its core idea remains the same, which is:

1. Set up a source input (original hierarchy).
2. Derive a follow-up input by transforming it in some way.
3. Ensure that the source inputs and follow-up outputs satisfy a known relationship.

If the third idea fails, this will indicate that the system has violated a property that should always hold, showing a clear evidence of a bug [1].

### Example 1: Inheritance (INH-01) Transitive Closure

#### What property does this really test?

In any given role hierarchy, let us say we have three roles: ROLE_A, ROLE_B, and ROLE_C. The relation is defined by: ROLE_B > ROLE_C. ROLE_A should be able to reach ROLE_C indirectly. This is called the transitive property, where even though they have no direct edge in between, but the senior role (ROLE_A) should still be able to reach its junior role (ROLE_C). If this relationship breaks, then the senior role will quietly lose access to the downstream roles, this type of bug is called a permission-loss bug.

**The source input:**

A simple chain of roles, which are generated with a length between 3 and 8:

```
ROLE_LEVEL_0 > ROLE_LEVEL_1
ROLE_LEVEL_1 > ROLE_LEVEL_2
ROLE_LEVEL_2 > ROLE_LEVEL_3
```

**The follow-up input:** While there is no separate follow-up hierarchy, the relation is checked from the source, where the top role (ROLE_LEVEL_0) can reach every other role in the chain.

**The relation is defined by:**
`reachable(ROLE_LEVEL_0) ⊇ { ROLE_LEVEL_0, ROLE_LEVEL_1, ROLE_LEVEL_2, ROLE_LEVEL_3 }`

**Example of a violation:** if `reachable(ROLE_LEVEL_0)` does not contain `ROLE_LEVEL_3`, then the assertion fails.

### Example 2: Monotonicity (MON-01) Upward Monotonicity

#### What property does this really test?
If ROLE_MANAGER > ROLE_STAFF, this means that ROLE_MANAGER should have every permission that ROLE_STAFF has. This is the important property of role inheritance where the parent role has more permission than its children.

**The source input:**
A chain hierarchy which is generated at a random with depth between 2 and 6:
```
ROLE_LEVEL_0 > ROLE_LEVEL_1
ROLE_LEVEL_1 > ROLE_LEVEL_2
```

**The follow-up input:** it still has the same hierarchy and relation checks that every adjacent parent-child pair in the chain, defined by:
> For every adjacent pair (parent, child):
> `reachable(parent) ⊇ reachable(child)`

**Example of a violation:** if `reachable(ROLE_LEVEL_1)` has a role that `reachable(ROLE_LEVEL_0)` does not have, the parent has fewer permissions than its child. This type of bug is called a permission-loss bug. If it is a serious bug, it can lead to wrong users being blocked from their resources.

### Example 3: Equivalence (EQ-01) Declaration Order Independence

#### What property does this really test?
This test ensures that no matter what the order of how the hierarchy string is made, it should not make any difference in the output. This is a consistency property where the system should behave like a mathematical set, instead of a parser which depends on the line ordering [2].

**The source input:** role hierarchy 1
```
ROLE_ADMIN > ROLE_USER
ROLE_USER > ROLE_GUEST
ROLE_ADMIN > ROLE_MODERATOR
```

**The follow-up input:** role hierarchy 2
```
ROLE_USER > ROLE_GUEST
ROLE_ADMIN > ROLE_MODERATOR
ROLE_ADMIN > ROLE_USER
```

**The relation:**
For each role in `{ROLE_ADMIN, ROLE_USER, ROLE_GUEST, ROLE_MODERATOR}`:

`reachable(role, hierarchy1) = reachable(role, hierarchy2)`

Both hierarchies should produce the same reachable set for every role.

**Example of a violation:** if `ROLE_ADMIN` can reach `ROLE_GUEST` in hierarchy1 but not in hierarchy2, this means that something within the `RoleHierarchyImpl` class is sensitive to the declaration order.

### Summary
The three examples cover the three main categories, each with a pattern:
- The source sets up a valid hierarchy.
- The follow-up is either a re-examination of the same input (INH, MON) or a transformation of the source test (EQ).
- The relation is a property which must hold no matter of the specific roles used.

Since jqwik generates 150 random inputs per test [3], one MR can cover far more ground than a manual test would. Hence, one failing case is enough to flag a bug which jqwik is also able to shrink down to the simplest possible failing input to help the users understand what went wrong.

---

*Licence: CC BY 4.0. Code snippets: MIT.*

---

## References

[1] S. Segura, D. Towey, Z. Q. Zhou, and T. Y. Chen, "Metamorphic Testing: Testing the Untestable," *IEEE Software*, vol. 37, no. 3, pp. 46–53, May–Jun. 2020. https://doi.org/10.1109/MS.2018.2875968

[2] T. Y. Chen, F.-C. Kuo, H. Liu, P.-L. Poon, D. Towey, T. H. Tse, and Z. Q. Zhou, "Metamorphic Testing: A Review of Challenges and Opportunities," *ACM Computing Surveys*, vol. 51, no. 1, Article 4, Jan. 2018. https://doi.org/10.1145/3143561

[3] jqwik User Guide — `@Property(tries = N)` parameter. https://jqwik.net/docs/current/user-guide.html
