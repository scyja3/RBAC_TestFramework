package com.research.rbac_testing.complexity;

/*
* SRQ3: Does detection effectiveness change as hierarchy complexity grows?
*
* Each test has the parameter of (depth, width) combinations so that
* the output CSV shows detection rate at each complexity levels
*
* complexity levels used:
*   SMALL   (depth = 3, width = 2) [typical small app]
*   MEDIUM  (depth = 5, width = 3) [typical enterprise app]
*   LARGE   (depth = 10, width = 4) [complex hierarchy]
*   X-LARGE (depth = 20, width = 5) [stress scenario]
* */

import com.research.rbac_testing.helpers.SpringSecurityTestHelpers;
import com.research.rbac_testing.reports.JqwikResultsWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ComplexityScalingTest {

    // Inheritance: parent always reaches all the children at every complexity level
    @ParameterizedTest(name = "INH-SCALE depth={0} width={1}")
    @CsvSource({
            "3,  2",
            "5,  3",
            "10, 4",
            "20, 5"
    })
    void inheritance_scale(int depth, int width) {
        long startNano = System.nanoTime();
        boolean passed = false;

        try {
            RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
            hierarchy.setHierarchy(HierarchyBuilder.buildDeepWide(depth, width));

            Set<String> reachable = SpringSecurityTestHelpers.getReachableAuthorities(
                    hierarchy, "ROLE_LEVEL_0"
            );

            int expectedMinSize = depth;
            assertThat(reachable.size())
                    .as("Root should reach at least %d roles in depth=%d width=%d hierarchy",
                            expectedMinSize, depth, width)
                    .isGreaterThanOrEqualTo(expectedMinSize);

            passed = true;
        } finally {
            long durationMs = (System.nanoTime() - startNano) / 1_000;
            // unique ID per complexity level so SRQ3 CSV has one row per combination
            String id = "INH-SCALE-d" + depth + "w" + width;
            JqwikResultsWriter.write(id, "INHERITANCE", "PERMISSION_LOSS", passed, durationMs);
        }
    }

    // Monotonicity: adding a role never reduces existing reachability
    @ParameterizedTest(name = "MON-SCALE depth={0} width={1}")
    @CsvSource({
            "3,  2",
            "5,  3",
            "10, 4",
            "20, 5"
    })
    void monotonicity_scale(int depth, int width) {
        long startNano = System.nanoTime();
        boolean passed = false;

        try {
            String baseHierarchy     = HierarchyBuilder.buildDeepWide(depth, width);
            String extendedHierarchy = baseHierarchy + "\nROLE_LEVEL_0 > ROLE_NEW_LEAF";

            RoleHierarchyImpl base     = new RoleHierarchyImpl();
            RoleHierarchyImpl extended = new RoleHierarchyImpl();
            base.setHierarchy(baseHierarchy);
            extended.setHierarchy(extendedHierarchy);

            Set<String> reachBefore = SpringSecurityTestHelpers.getReachableAuthorities(
                    base, "ROLE_LEVEL_0"
            );
            Set<String> reachAfter = SpringSecurityTestHelpers.getReachableAuthorities(
                    extended, "ROLE_LEVEL_0"
            );

            assertThat(reachAfter)
                    .as("After adding a role, root should still reach all previous roles " +
                            "(depth=%d, width=%d)", depth, width)
                    .containsAll(reachBefore);

            assertThat(reachAfter.size())
                    .as("Reachability must grow or stay equal after adding a role")
                    .isGreaterThanOrEqualTo(reachBefore.size());

            passed = true;
        } finally {
            long durationMs = (System.nanoTime() - startNano) / 1_000;
            String id = "MON-SCALE-d" + depth + "w" + width;
            JqwikResultsWriter.write(id, "MONOTONICITY", "PERMISSION_LOSS", passed, durationMs);
        }
    }

    // Equivalence: two structurally identical hierarchies must behave the same
    @ParameterizedTest(name = "EQ-SCALE depth={0} width={1}")
    @CsvSource({
            "3,  2",
            "5,  3",
            "10, 4",
            "20, 5"
    })
    void equivalence_scale(int depth, int width) {
        long startNano = System.nanoTime();
        boolean passed = false;

        try {
            String h1 = HierarchyBuilder.buildDeepWide(depth, width);

            // structurally identical hierarchy with different role name prefix
            String h2 = HierarchyBuilder.buildDeepWide(depth, width)
                    .replace("ROLE_LEVEL_", "ROLE_MIRROR_");

            RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
            RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
            impl1.setHierarchy(h1);
            impl2.setHierarchy(h2);

            Set<String> reach1 = SpringSecurityTestHelpers.getReachableAuthorities(
                    impl1, "ROLE_LEVEL_0"
            );
            Set<String> reach2 = SpringSecurityTestHelpers.getReachableAuthorities(
                    impl2, "ROLE_MIRROR_0"
            );

            // isomorphic hierarchies must have the same reachability count
            assertThat(reach1.size())
                    .as("Isomorphic hierarchies must produce same reachability count " +
                            "(depth=%d, width=%d)", depth, width)
                    .isEqualTo(reach2.size());

            passed = true;
        } finally {
            long durationMs = (System.nanoTime() - startNano) / 1_000;
            String id = "EQ-SCALE-d" + depth + "w" + width;
            JqwikResultsWriter.write(id, "EQUIVALENCE", "PRIVILEGE_LEAKAGE|PERMISSION_LOSS", passed, durationMs);
        }
    }

    // helper: builds hierarchy strings of given depth and width
    static class HierarchyBuilder {
        static String buildDeepWide(int depth, int width) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;

            for (int level = 0; level < depth; level++) {
                String parent = "ROLE_LEVEL_" + level;

                for (int branch = 0; branch < width; branch++) {
                    String child = (branch == 0)
                            ? "ROLE_LEVEL_" + (level + 1)
                            : "ROLE_LEVEL_" + level + "_BRANCH_" + branch;

                    if (!first) sb.append("\n");
                    sb.append(parent).append(" > ").append(child);
                    first = false;
                }
            }

            return sb.toString();
        }
    }
}