package com.research.rbac_testing;

import com.research.rbac_testing.bugs.ConcurrentModificationTests;
import com.research.rbac_testing.complexity.ComplexityScalingTest;
import com.research.rbac_testing.metamorphic.EquivalenceBasedMRs;
import com.research.rbac_testing.metamorphic.InheritanceBasedMRs;
import com.research.rbac_testing.metamorphic.MonotonicityBasedMRs;

import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SuiteDisplayName;
/**
 * Master Test Suite for RBAC Metamorphic Testing Framework
 *
 * This suite runs all metamorphic relation tests organized by category:
 * 1. Inheritance-Based MRs (10 tests)
 * 2. Monotonicity-Based MRs (10 tests)
 * 3. Equivalence-Based MRs (10 tests)
 * 4. Concurrency-Based MRs (5 tests)
 *
 * Total: 35 MRs (30 MRs and HMRs across three categories, plus 5 concurrency tests)
 *
 * Research Questions Addressed:
 * - Main RQ: Which MR category is most effective at detecting bugs?
 * - Sub-RQ1: How does effectiveness vary across bug types?
 * - Sub-RQ2: Computational cost comparison between categories
 * - Sub-RQ3: Effectiveness across different hierarchy complexities
 *
 */
@Suite
@SuiteDisplayName("RBAC Metamorphic Testing Framework - Full Test Suite")
@SelectClasses({
        InheritanceBasedMRs.class,          // Tests structural properties of hierarchies
        MonotonicityBasedMRs.class,         // Tests permission flow properties
        EquivalenceBasedMRs.class,          // Tests consistency of representations
        ConcurrentModificationTests.class,
        ComplexityScalingTest.class
})

public class RBACMetamorphicTestSuite {

}