# Metamorphic Testing

## What is an Oracle Problem?
Typically, when you test software or a problem, you should already imagine or know which answer is correct and which is not. This is called the test oracle, which, by the formal definition, is the mechanism that tells you whether a test passed or failed [1].
> Example: An app that we are all familiar with, Google Translate. You enter an english sentence, and it translates it into Chinese. Unless you have already spoken Chinese fluently, how do you know that the app is translating your english sentence correctly? This is the oracle problem, you have no reliable way to determine if the output of the app is correct or not.

This brings us to our current project: how do you verify a complex role hierarchy is correct without having to rebuild the entire system?

## Metamorphic Testing to Alleviate the Problem
This brings us to a solution which does not exactly solve the problem; however, it does address and alleviate the issue [2]. Metamorphic testing checks the relationships between multiple related inputs and their corresponding outputs [1]. To check the relationship, we use metamorphic relation, where we do not need to know the correct answer but need to know how the answer should relate to another. An MR basically works as follows: if changing an input in a specific way, then the output must also change (or stay the same) in a predictable way.

> Example 1: When you search something in Google, let's say "super red cars" and "Super Red Cars," both should be returning the same results (this is a case of insensitivity MR).
>
> Example 2: Finding the shortest route from place A to place B should return the same distance as the shortest route from place B to place A. This is known as symmetry MR.
>
> The Bottom line is, you don't need to know the exact shortest route, you need to check if the property holds.

## What does an MR look like?
Every MR has two parts:
- source test case: the original input and its output
- follow-up test case: a transformed input and its output

Between those two outputs, the MR will define the relationships, which, if the relationship is violated, means that a bug is detected [2]. The thing that distinguishes metamorphic testing and traditional testing is that once a violation is detected in a correctly defined MR, it points to a genuine inconsistency in the system's behavior.
> Note: There's a difference between MRs and HMRs (hypothesised MRs) [3]
> - MR: This essentially guarantees that something must always be true about the system, regardless of the specific input values used. Hence, you do not need to know the exact right answer to spot something is wrong.
> - HMR: It is not confirmed 100% if it can be a universal rule, however, still making a well-reasoned assumption and checks whether the system agrees.

## Why is MT so useful for security systems?
Many applications we use daily rely on security systems to control who can access what [4]. For example, students that use the school's Moodle web page. Students are able to view their grades but are not able to edit them. A lecturer can view and submit the students but cannot access another lecturer's module. A school administrator can do both and more. Nobody explicitly programmed every combination of "students cannot edit grades"; the system defines the hierarchy of roles, and access flows down automatically. If the system is set up incorrectly, other students may gain permission that they aren't supposed to have in the first place. He/she might be able to change their own grades, hence making the integrity of the entire system questionable.

## Spring Security
This is the problem that Spring Security has to solve. As one of the widely used security frameworks in the Java ecosystem, it provides a built-in mechanism called `RoleHierarchyImpl` for defining these role relationships. For example, `ROLE_ADMIN` inherits all permissions from `ROLE_MANAGER`, which in turn inherits all permissions from `ROLE_USER`. Instead of manually listing every permission that each role has, developers define the hierarchy once, and the framework will automatically do the rest. Therefore, this project will apply metamorphic testing to the specific component and raise the question of "How sure can we be that this really works, given how important it is to application security?"

## What are the limitations, though?
Given some evidence that MT has alleviated the oracle problem, it does not guarantee complete coverage [2]. It only tests the properties that are thought to formalise; hence, a passing MR does not mean that the software is bug-free. It just means that there's no violation in that particular relation across the inputs tested.

---

*Licence: CC BY 4.0. Code snippets: MIT.*

---
## References

[1] T. Y. Chen, F.-C. Kuo, H. Liu, P.-L. Poon, D. Towey, T. H. Tse, and Z. Q. Zhou, "Metamorphic Testing: A Review of Challenges and Opportunities," *ACM Computing Surveys*, vol. 51, no. 1, Article 4, Jan. 2018. https://doi.org/10.1145/3143561

[2] S. Segura, D. Towey, Z. Q. Zhou, and T. Y. Chen, "Metamorphic Testing: Testing the Untestable," *IEEE Software*, vol. 37, no. 3, pp. 46–53, May–Jun. 2020. https://doi.org/10.1109/MS.2018.2875968

[3] Z. Ying, D. Towey, et al., "Hypothesised Metamorphic Relations," 2025. *(Framework used for MR/HMR classification in this project.)*

[4] T. Y. Chen, F.-C. Kuo, W. Ma, W. Susilo, D. Towey, J. Voas, and Z. Q. Zhou, "Metamorphic Testing for Cybersecurity," *IEEE Computer*, vol. 49, no. 6, pp. 48–55, Jun. 2016. https://doi.org/10.1109/MC.2016.185
