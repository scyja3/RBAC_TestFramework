# Role-Based Access Control (RBAC)

## What is Access Control?
In computer security, the 'CIA triad' is a foundational concept that's widely known in education and practice [1]. This term means only authorised users can access the data (confidentiality) and modify it (integrity); they should always have access to it. Access control consisted of all three concepts; without it, unauthorised users can access data for which they lack authorisation. Which is why they act as a mechanism which enforces rules for users to access data. Every access control model is always checking the user/subject who is trying to access the resource, what they want to access, and what they will do with it.

## What problem does role-based access solve?

Before the role-based system exists, people have to manually assign permission to each role. If a new role is added, the permission would have to be manually modified. Such an approach would not be efficient if a system had thousands of users/roles added simultaneously. This process will be very inefficient and prone to errors, where some roles might have got the wrong permission, leading to a serious security problem. Not to mention when a policy changes, the update has to be applied to all the existing roles.

This scenario is where role-based access solves the problem [2].
It automatically groups the permissions,
and when a role is assigned to a user, they will naturally inherit every permission that the role has.
For example, a school's admin would have to manually assign a lecturer, so they can "view grades,
submit grades, and access the grade book" vs. simply assigning ROLE_LECTURER.

## The main RBAC concepts [2]:

> - **role:** A named collection of permissions (e.g. ROLE_ADMIN, ROLE_USER)
> - **permission:** the ability to do a specific action to a specific resource
> - **user-role assignment:** which users have which roles
> - **role-permission assignment:** which roles can access which permission

## Role Hierarchy

While RBAC groups permissions into roles, role hierarchy adds another concept, which is called inheritance [2]. This indicates that senior roles can inherit all the permissions from the roles below within the inheritance. For example, on a school's website, ROLE_ADMIN > ROLE_LECTURER > ROLE_STUDENT, meaning ROLE_ADMIN has all the permissions from ROLE_LECTURER and ROLE_STUDENT. This removes any redundancy within the system, so the admin does not have to assign each of the permissions twice. Even though this concept sounds simple, one small misconfiguration will ruin the whole system. For example, if the lecturer's permission to modify a student's grades got mixed up with the student's permission, this would break the integrity of the whole system. However, if this mistake actually happens, an admin wouldn't be able to see the issue directly since the system would not raise any bugs.

An important concept within the role hierarchy exists, called transitivity. This is the idea: if A connects to B, B also connects to C, then A must also be connected to C, even if there's no direct connection. For example, if `ROLE_ADMIN` > `ROLE_LECTURER` and `ROLE_LECTURER` > `ROLE_STUDENT`, then `ROLE_ADMIN` also reaches `ROLE_STUDENT`. This is where some bugs might silently appear and the reason why RBAC suffers from an oracle problem. Within the project, there are three categories—inheritance, monotonicity, and equivalence—and the first category's MR aims to check for this aspect (specifically MR-INH-01 and MR-INH-06, the first and sixth MR within the inheritance file).

## What are the target bugs for this project?

> - **privilege leakage:** a role gains the wrong permission, usually happens through incorrect inheritance edge
> - **permission loss:** a role does not have the permission it was supposed to have, usually happens when a path within the hierarchy is broken
> - **cyclic dependencies:** creates a cycle between the roles, e.g. role A inherits from role B but it cycled back to role A, unusual bugs, but this would be hard to detect.
> - **constraint violations:** the hierarchy breaks a rule that it is designed to maintain, like not letting two roles work together

These problems are challenging to detect in traditional testing since to do so, you would have to write down every possible role-permission pair manually [3].

## Why does Spring Security's RoleHierarchyImpl a good target?

This project targets a widely used framework in the Java ecosystem, which is Spring Security [4]. Big companies in healthcare or even banking applications integrate Spring Security as part of their backend infrastructure. As one of a widely deployed frameworks, finding bugs within the system would contribute to improving the security in widely used applications.

The next section will explain how metamorphic relations map directly to the hierarchy properties.

---

*Licence: CC BY 4.0. Code snippets: MIT.*

---

## References

[1] W. Stallings, *Computer Security: Principles and Practice*, 4th ed. Pearson, 2018.

[2] American National Standards Institute / International Committee for Information Technology Standards, "Role Based Access Control," ANSI INCITS 359-2004, 2004.

[3] C. D. N. Damasceno, P. C. Masiero, and A. Simao, "Similarity testing for role-based access control systems," *Journal of Software Engineering Research and Development*, vol. 6, no. 1, Jan. 2018. https://doi.org/10.1186/s40411-017-0045-x

[4] Spring Security Reference Documentation, https://docs.spring.io/spring-security/reference/
