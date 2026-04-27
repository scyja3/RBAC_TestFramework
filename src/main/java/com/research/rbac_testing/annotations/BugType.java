package com.research.rbac_testing.annotations;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*
* helps to answer the research question
*
* usage:
* @BugType(
*       category = MRCategory.INHERITANCE,
*       bugTypes = {BugTypeEnum.PRIVILEGE_LEAKAGE},
*       mrId = "INH-1"
* )
*
* */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BugType {

    // which categories does the relation belongs to
    MRCategory category();

    // which bug types does this MR is designed to detect (can be multiple)
    BugTypeEnum[] bugTypes();

    // the MR identifier: e.g., "INH-1", "MON-3", "EQ-7"
    String mrId();

    String description() default "";

    enum MRCategory {
        INHERITANCE,
        MONOTONICITY,
        EQUIVALENCE
    }

    enum BugTypeEnum {
        // a role can gain access that it should not have
        PRIVILEGE_LEAKAGE,

        // a role loses the access that it should keep
        PERMISSION_LOSS,

        // a circular dependency that exists in the hierarchy
        CYCLIC_DEPENDENCY,

        // a structural rule of the hierarchy is broken
        CONSTRAINT_VIOLATION
    }
}
