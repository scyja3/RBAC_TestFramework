package com.research.rbac_testing.metamorphic;

import com.research.rbac_testing.annotations.BugType;
import com.research.rbac_testing.helpers.SpringSecurityTestHelpers;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.*;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Equivalence-Based Metamorphic Relations
 *
 * MRs (Bug-Finding): EQ-01, EQ-04, EQ-05, EQ-08, EQ-09
 * HMRs (Exploratory): EQ-02, EQ-03, EQ-06, EQ-07, EQ-10
 *
 * Classification based on Ying et al. (2025) framework
 */

public class EquivalenceBasedMRs {

    private long    _mrStartNano;
    private BugType _mrAnnotation;
    private boolean _mrPassed;

    @BeforeProperty
    void _beforeMR() {
        _mrStartNano = System.nanoTime();
        _mrAnnotation = null;
        _mrPassed = false;
    }

    @AfterProperty
    void _afterMR() {
        if (_mrAnnotation == null) return;
        long durationMs = (System.nanoTime() - _mrStartNano) / 1_000_000;
        String bugCsv = Arrays.stream(_mrAnnotation.bugTypes())
                              .map(Enum::name)
                              .collect(Collectors.joining("|"));
        com.research.rbac_testing.reports.JqwikResultsWriter.write(
            _mrAnnotation.mrId(),
            _mrAnnotation.category().name(),
            bugCsv,
            _mrPassed,
            durationMs
        );
    }

    /** Call as FIRST line of each @Property: registers the MR annotation. */
    void _r(String mrId) {
        for (java.lang.reflect.Method m : getClass().getDeclaredMethods()) {
            BugType b = m.getAnnotation(BugType.class);
            if (b != null && mrId.equals(b.mrId())) { _mrAnnotation = b; return; }
        }
    }

    /** Call as LAST line of each @Property body: marks test as passed. */
    void _pass() { _mrPassed = true; }
    // MR-EQ-01: declaration order independence
    @Property(tries = 150)
    @Label("EQ-01: role hierarchy should be independent of declaration order")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "EQ-01",
            description = "Different order of the same hierarchy should produce identical reachability for all roles"
    )
    void declarationOrderIndependence() {
        _r("EQ-01");
        // source: define the hierarchy in one order
        String hierarchy1 = "ROLE_ADMIN > ROLE_USER\n" + "ROLE_USER > ROLE_GUEST\n" + "ROLE_ADMIN > ROLE_MODERATOR";

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(hierarchy1);

        // follow up: define the same hierarchy in different order
        String hierarchy2 = "ROLE_USER > ROLE_GUEST\n" + "ROLE_ADMIN > ROLE_MODERATOR\n" + "ROLE_ADMIN > ROLE_USER";

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(hierarchy2);

        // MR: both should produce the same results
        List<String> testRoles = Arrays.asList("ROLE_ADMIN", "ROLE_USER", "ROLE_GUEST", "ROLE_MODERATOR");

        for (String role : testRoles) {
            Set<String> reach1 = SpringSecurityTestHelpers.getReachableAuthorities(impl1, role);
            Set<String> reach2 = SpringSecurityTestHelpers.getReachableAuthorities(impl2, role);

            assertThat(reach1)
                    .as("Role %s should have identical permissions regardless of declaration order", role)
                    .isEqualTo(reach2);
        }
    _pass();
    }

    // HMR-EQ-02: transitive redundancy equivalence
    @Property(tries = 150)
    @Label("EQ-02: explicit transitive edges should be redundant")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "EQ-02",
            description = "HMR: adding redundant transitive edge should not change the reachability for any roles "
    )
    void transitiveRedundancyEquivalence(@ForAll @IntRange(min = 3, max = 6) int chainLength) {
        _r("EQ-02");
        // source: minimal chain (only adjacent edges)
        Map<String, List<String>> minimal = new HashMap<>();
        List<String> roles = new ArrayList<>();

        for (int i = 0; i < chainLength; i++) {
            roles.add("ROLE_" + i);
        }

        for (int i = 0; i < roles.size() - 1; i++) {
            minimal.put(roles.get(i), Collections.singletonList(roles.get(i + 1)));
        }

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(minimal));

        // follow-up: add redundant transitive edge (top > bottom)
        Map<String, List<String>> redundant = new HashMap<>(minimal);
        String topRole = roles.get(0);
        String bottomRole = roles.get(roles.size() - 1);

        // add explicit transitive edge
        List<String> topChildren = new ArrayList<>(redundant.get(topRole));
        topChildren.add(bottomRole);
        redundant.put(topRole, topChildren);

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(redundant));

        // MR: both hierarchies should produce identical results
        for (String role : roles) {
            Set<String> reach1 = SpringSecurityTestHelpers.getReachableAuthorities(impl1, role);
            Set<String> reach2 = SpringSecurityTestHelpers.getReachableAuthorities(impl2, role);

            assertThat(reach1)
                    .as("Role %s permissions should be identical with/without redundant edges", role)
                    .isEqualTo(reach2);
        }
    _pass();
    }

    // HMR-EQ-03: name invariance property
    @Property(tries = 150)
    @Label("EQ-03: Hierarchy structure should be invariant to role naming")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "EQ-03",
            description = "HMR: renaming roles should not change hierarchy depth or reachability count"
    )
    void nameInvarianceProperty() {
        _r("EQ-03");
        Map<String, List<String>> original = new HashMap<>();
        original.put("ROLE_A", Arrays.asList("ROLE_B", "ROLE_C"));
        original.put("ROLE_B", Collections.singletonList("ROLE_D"));
        original.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(original));

        // follow up: renamed hierarchy
        Map<String, List<String>> renamed = new HashMap<>();
        renamed.put("ROLE_X", Arrays.asList("ROLE_Y", "ROLE_Z"));
        renamed.put("ROLE_Y", Collections.singletonList("ROLE_W"));
        renamed.put("ROLE_Z", Collections.singletonList("ROLE_W"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(renamed));

        // MR: Structural properties must be identical
        // 1. depth should be same
        Map<String, Set<String>> map1 = SpringSecurityTestHelpers.getInternalRoleMap(impl1);
        Map<String, Set<String>> map2 = SpringSecurityTestHelpers.getInternalRoleMap(impl2);

        int depth1 = SpringSecurityTestHelpers.calculateDepth(map1);
        int depth2 = SpringSecurityTestHelpers.calculateDepth(map2);

        assertThat(depth1)
                .as("Renamed hierarchy should have same depth")
                .isEqualTo(depth2);

        // 2. each role's reachability count should match
        Set<String> reach_A = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_A");
        Set<String> reach_X = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_X");

        assertThat(reach_A.size())
                .as("Top roles should reach same number of authorities")
                .isEqualTo(reach_X.size());

        Set<String> reach_D = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_D");
        Set<String> reach_W = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_W");

        assertThat(reach_D.size())
                .as("Bottom roles should reach same number of authorities")
                .isEqualTo(reach_W.size());
    _pass();
    }

    // MR-EQ-04: representation format equivalence
    @Example
    @Label("EQ-04: Different string formats of same hierarchy should be equivalent")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "EQ-04",
            description = "Same hierarchy with different whitespace or extra new lines must produce identical reachability"
    )
    void representationFormatEquivalence() {
        _r("EQ-04");
        // source: hierarchy with one formatting style
        String format1 = "ROLE_A > ROLE_B\nROLE_B > ROLE_C\n";

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(format1);

        // follow-up: same hierarchy with different whitespace
        String format2 = "ROLE_A>ROLE_B\n ROLE_B >ROLE_C ";

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(format2);

        // follow-up2: with extra newlines
        String format3 = "ROLE_A > ROLE_B\n\nROLE_B > ROLE_C\n";

        RoleHierarchyImpl impl3 = new RoleHierarchyImpl();
        impl3.setHierarchy(format3);

        // MR: all three should produce identical results
        List<String> testRoles = Arrays.asList("ROLE_A", "ROLE_B", "ROLE_C");

        for (String role : testRoles) {
            Set<String> reach1 = SpringSecurityTestHelpers.getReachableAuthorities(impl1, role);
            Set<String> reach2 = SpringSecurityTestHelpers.getReachableAuthorities(impl2, role);
            Set<String> reach3 = SpringSecurityTestHelpers.getReachableAuthorities(impl3, role);

            assertThat(reach1)
                    .as("Role %s permissions should be independent of string formatting", role)
                    .isEqualTo(reach2)
                    .isEqualTo(reach3);
        }
    _pass();
    }

    // MR-EQ-05: permutation invariance
    @Property(tries = 150)
    @Label("EQ-05: Edge permutations should produce equivalent hierarchies")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "EQ-05",
            description = "Three different edge permutations should produce an identical reachability"
    )
    void edgePermutationInvariance() {
        _r("EQ-05");
        // create a list of edges
        List<String> edges = Arrays.asList(
                "ROLE_A > ROLE_B",
                "ROLE_A > ROLE_C",
                "ROLE_B > ROLE_D",
                "ROLE_C > ROLE_D",
                "ROLE_A > ROLE_E"
        );

        // source: Edges in original order
        String hierarchy1 = String.join("\n", edges);
        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(hierarchy1);

        // follow-up: Test multiple random permutations
        List<String> testRoles = Arrays.asList("ROLE_A", "ROLE_B", "ROLE_C", "ROLE_D", "ROLE_E");

        // capture the original reachability
        Map<String, Set<String>> originalReach = new HashMap<>();
        for (String role : testRoles) {
            originalReach.put(role,
                    SpringSecurityTestHelpers.getReachableAuthorities(impl1, role));
        }

        // test 3 different permutations
        List<List<String>> permutations = Arrays.asList(
                Arrays.asList(edges.get(4), edges.get(2), edges.get(0), edges.get(3), edges.get(1)),
                Arrays.asList(edges.get(3), edges.get(1), edges.get(4), edges.get(0), edges.get(2)),
                Arrays.asList(edges.get(2), edges.get(4), edges.get(1), edges.get(0), edges.get(3))
        );

        for (int i = 0; i < permutations.size(); i++) {
            String permutedHierarchy = String.join("\n", permutations.get(i));
            RoleHierarchyImpl implPermuted = new RoleHierarchyImpl();
            implPermuted.setHierarchy(permutedHierarchy);

            // MR: each permutation should match original
            for (String role : testRoles) {
                Set<String> permutedReach = SpringSecurityTestHelpers.getReachableAuthorities(implPermuted, role);

                assertThat(permutedReach)
                        .as("Permutation %d: Role %s should have same permissions", i + 1, role)
                        .isEqualTo(originalReach.get(role));
            }
        }
    _pass();
    }

    // HMR-EQ-06: role renaming invariance
    @Property(tries = 150)
    @Label("EQ-06: renaming roles shouldn't change the access control decisions")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "EQ-06",
            description = "HMR: renaming all the roles in a 3-level chain should not change any of the top tier reachability count"
    )
    void roleRenamingInvariance() {
        _r("EQ-06");
        // source: hierarchy with the original role names
        Map<String, List<String>> original = new HashMap<>();
        original.put("ROLE_MANAGER", Collections.singletonList("ROLE_EMPLOYEE"));
        original.put("ROLE_EMPLOYEE", Collections.singletonList("ROLE_INTERN"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(original));

        int originalDepth = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_MANAGER").size();

        // follow-up: renaming but with the same structure
        Map<String, List<String>> renamed_roles = new HashMap<>();
        renamed_roles.put("ROLE_SUPERVISOR", Collections.singletonList("ROLE_WORKER"));
        renamed_roles.put("ROLE_WORKER", Collections.singletonList("ROLE_TRAINEE"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(renamed_roles));

        int renamedDepth = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_SUPERVISOR").size();

        // MR: structural properties should be identical
        assertThat(renamedDepth).as("Renamed hierarchy should have the same depth as original")
                .isEqualTo(originalDepth);

        // both top roles should reach the same number of subordinates
        assertThat(originalDepth).isEqualTo(3);
        assertThat(renamedDepth).isEqualTo(3);
    _pass();
    }

    // HMR-EQ-07: hierarchy isomorphism
    @Property(tries = 150)
    @Label("EQ-07: isomorphic hierarchies should produce equivalent access decisions")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "EQ-07",
            description = "HMR: two isomorphic hierarchies should have matching reachability counts for all the corresponding roles"
    )
    void hierarchyIsomorphism() {
        _r("EQ-07");
        // source: hierarchy H1 with specific structure
        Map<String, List<String>> h1 = new HashMap<>();
        h1.put("ROLE_H1_A", Arrays.asList("ROLE_H1_B", "ROLE_H1_C"));
        h1.put("ROLE_H1_B", Collections.singletonList("ROLE_H1_D"));
        h1.put("ROLE_H1_C", Collections.singletonList("ROLE_H1_E"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(h1));

        Set<String> h1TopReach = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_H1_A");
        Set<String> h1LeftReach = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_H1_B");

        // follow-up: isomorphic hierarchy h2 with different names
        Map<String, List<String>> h2 = new HashMap<>();
        h2.put("ROLE_H2_A", Arrays.asList("ROLE_H2_B", "ROLE_H2_C"));
        h2.put("ROLE_H2_B", Collections.singletonList("ROLE_H2_D"));
        h2.put("ROLE_H2_C", Collections.singletonList("ROLE_H2_E"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(h2));

        Set<String> h2TopReach = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_H2_A");
        Set<String> h2LeftReach = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_H2_B");

        // MR: corresponding roles should have same reachability counts
        assertThat(h2TopReach.size())
                .as("Isomorphic top roles should reach same number of roles")
                .isEqualTo(h1TopReach.size());

        assertThat(h2LeftReach.size())
                .as("Isomorphic left roles should reach same number of roles")
                .isEqualTo(h1LeftReach.size());

        // verify diamond structure is preserved
        assertThat(h1TopReach).hasSize(5);
        assertThat(h2TopReach).hasSize(5);
    _pass();
    }

    // MR-EQ-08: permission set commutativity
    @Property(tries = 150)
    @Label("EQ-08: order of permission assignment should not matter")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "EQ-08",
            description = "Assigning the same permissions in different orders should produce an identical permission sets"
    )
    void permissionSetCommutativity() {
        _r("EQ-08");
        // source: assign each permission in order P1, P2, P3
        Map<String, List<String>> order123 = new HashMap<>();
        order123.put("ROLE_USER", Arrays.asList("ROLE_PERM1", "ROLE_PERM2", "ROLE_PERM3"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(order123));

        Set<String> perms123 = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_USER");

        // follow-up: assign the same permission in different order P3, P1, P2
        Map<String, List<String>> order312 = new HashMap<>();
        order312.put("ROLE_USER", Arrays.asList("ROLE_PERM3", "ROLE_PERM1", "ROLE_PERM2"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(order312));

        Set<String> perms312 = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_USER");

        // follow-up2: another different order P2, P3, P1
        Map<String, List<String>> order231 = new HashMap<>();
        order231.put("ROLE_USER", Arrays.asList("ROLE_PERM2", "ROLE_PERM3", "ROLE_PERM1"));

        RoleHierarchyImpl impl3 = new RoleHierarchyImpl();
        impl3.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(order231));

        Set<String> perms231 = SpringSecurityTestHelpers.getReachableAuthorities(impl3, "ROLE_USER");

        // MR: all ordering should produce identical permission sets
        assertThat(perms312).as("Permission order 321 should be equal to order 123")
                .isEqualTo(perms123);
        assertThat(perms231).as("Permission order 231 should be equal to order 123")
                .isEqualTo(perms123);

        assertThat(perms123).as("Should have all roles (user + 3 perms)")
                .hasSize(4)
                .contains("ROLE_USER", "ROLE_PERM1", "ROLE_PERM2", "ROLE_PERM3");
    _pass();
    }

    // MR-EQ-09: alternative path equivalence
    @Property(tries = 150)
    @Label("EQ-9: different inheritance path to same permission should be equivalent")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "EQ-09",
            description = "ROLE_TARGET should be reachable through both dual path and single path configurations"
    )
    void alternativePathEquivalence() {
        _r("EQ-09");
        // source: permission reachable through two paths
        Map<String, List<String>> dualPath = new HashMap<>();
        dualPath.put("ROLE_TOP", Arrays.asList("ROLE_PATH_A", "ROLE_PATH_B"));
        dualPath.put("ROLE_PATH_A", Collections.singletonList("ROLE_TARGET"));
        dualPath.put("ROLE_PATH_B", Collections.singletonList("ROLE_TARGET"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(dualPath));

        Set<String> topDual = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_TOP");

        // follow-up: same permission through a single path
        Map<String, List<String>> singlePath = new HashMap<>();
        singlePath.put("ROLE_TOP", Collections.singletonList("ROLE_PATH_A"));
        singlePath.put("ROLE_PATH_A", Collections.singletonList("ROLE_TARGET"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(singlePath));

        Set<String> topSingle = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_TOP");

        // MR: both should reach target
        assertThat(topDual).as("Dual path should reach TARGET")
                .contains("ROLE_TARGET");

        assertThat(topSingle).as("Single path should reach TARGET")
                .contains("ROLE_TARGET");

        // dual path should also include PATH_B
        assertThat(topDual).as("Dual path should include both intermediary roles")
                .contains("ROLE_PATH_A", "ROLE_PATH_B", "ROLE_TARGET");

        assertThat(topSingle).as("Single path should only have one intermediary")
                .contains("ROLE_PATH_A", "ROLE_TARGET")
                .doesNotContain("ROLE_PATH_B");
    _pass();
    }

    // HMR-EQ-10: hierarchy flattening equivalence
    @Property(tries = 150)
    @Label("EQ-10: Deep hierarchy should equal its flattened equivalent")
    @BugType(
            category = BugType.MRCategory.EQUIVALENCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "EQ-10",
            description = "HMR: the flattened hierarchy must reach the same roles as deep chain"
    )
    void hierarchyFlatteningEquivalence(@ForAll @IntRange(min = 3, max = 5) int depth) {
        _r("EQ-10");
        // source: deep nested hierarchy A > B > C > D
        Map<String, List<String>> deep = new HashMap<>();
        List<String> roles = new ArrayList<>();

        for (int i = 0; i < depth; i++) {
            roles.add("ROLE_DEPTH_" + i);
        }

        for (int i = 0; i < roles.size() - 1; i++) {
            deep.put(roles.get(i), Collections.singletonList(roles.get(i + 1)));
        }

        RoleHierarchyImpl implDeep = new RoleHierarchyImpl();
        implDeep.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(deep));

        Set<String> topDeep = SpringSecurityTestHelpers.getReachableAuthorities(implDeep, roles.get(0));

        // follow-up: flattened version where top connects directly to all
        Map<String, List<String>> flat = new HashMap<>();
        List<String> allDescendants = new ArrayList<>(roles.subList(1, roles.size()));
        flat.put(roles.get(0), allDescendants);

        RoleHierarchyImpl implFlat = new RoleHierarchyImpl();
        implFlat.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(flat));

        Set<String> topFlat = SpringSecurityTestHelpers.getReachableAuthorities(implFlat, roles.get(0));

        // MR: both should reach the same roles from top
        assertThat(topFlat).as("Flattened hierarchy should reach all same roles as deep hierarchy")
                .isEqualTo(topDeep);

        assertThat(topFlat).as("Should have all %d roles", depth).hasSize(depth);

        // verify if all the descendants are reachable
        for (String role : roles) {
            assertThat(topFlat).as("Top should reach %s in flattened version", role).contains(role);
            assertThat(topDeep).as("Top should reach %s in deep version", role).contains(role);
        }
    _pass();
    }
}