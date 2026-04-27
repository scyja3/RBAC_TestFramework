package com.research.rbac_testing.metamorphic;

import com.research.rbac_testing.annotations.BugType;
import com.research.rbac_testing.helpers.SpringSecurityTestHelpers;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.*;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Inheritance-Based Metamorphic Relations
 *
 * MRs (Bug-Finding): INH-01, INH-02, INH-03, INH-05, INH-06, INH-08, INH-09, INH-10
 * HMRs (Exploratory): INH-04, INH-07
 *
 * Classification based on Ying et al. (2025) framework
 */

public class InheritanceBasedMRs {

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
    // MR-INH-01: transitive closure property
    @Property(tries = 150)
    @Label("INH-01: Transitive closure must work correctly in role chains")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "INH-01",
            description = "IF A > B, then reachable(A) ⊇ reachable(B)"
    )
    void transitiveClosureInChain(@ForAll @IntRange(min = 3, max = 8) int chainLength) {
        _r("INH-01");
        // building a simple chain hierarchy
        Map<String, List<String>> hierarchy = new HashMap<>();
        List<String> roles = new ArrayList<>();

        for (int i = 0; i < chainLength; i++) {
            roles.add("ROLE_LEVEL_" + i);
        }

        // creating a chain
        for (int i = 0; i < roles.size() - 1; i++) {
            hierarchy.put(roles.get(i), Collections.singletonList(roles.get(i + 1)));
        }

        // source: set up the hierarchy
        RoleHierarchyImpl impl = new RoleHierarchyImpl();
        impl.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(hierarchy));

        // follow up: check if the top role reaches all roles in chain
        String topRole = roles.get(0);
        Set<String> reachable = SpringSecurityTestHelpers.getReachableAuthorities(impl, topRole);

        // MR: top roles must reach all roles in the chain
        assertThat(reachable)
                .as("Top role %s should transitively reach all %d roles in chain", topRole, chainLength)
                .containsAll(roles);
    _pass();
    }

    // MR-INH-02: antisymmetry property
    @Property(tries = 150)
    @Label("INH-02: Role hierarchy must maintain antisymmetry (no cycles)")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.CYCLIC_DEPENDENCY},
            mrId = "INH-02",
            description = "Reversing relationship A > B, B > A must be rejected as a cycle"
    )
    void antisymmetryPreventsSimpleCycles() {
        _r("INH-02");
        // source: create a simple hierarchy A > B
        Map<String, List<String>> hierarchy1 = new HashMap<>();
        hierarchy1.put("ROLE_SENIOR", Collections.singletonList("ROLE_JUNIOR"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(hierarchy1));

        Set<String> seniorReaches1 = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_SENIOR");
        Set<String> juniorReaches1 = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_JUNIOR");

        // follow up: try to add reverse relationship B > A (creating cycle)
        Map<String, List<String>> hierarchy2 = new HashMap<>();
        hierarchy2.put("ROLE_SENIOR", Collections.singletonList("ROLE_JUNIOR"));
        hierarchy2.put("ROLE_JUNIOR", Collections.singletonList("ROLE_SENIOR"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();

        // MR: Spring Security should reject cyclic hierarchies
        // This is the correct behavior - cycles violate RBAC antisymmetry property
        assertThatThrownBy(() -> {
            impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(hierarchy2));
        }).isInstanceOf(org.springframework.security.access.hierarchicalroles.CycleInRoleHierarchyException.class)
                .hasMessageContaining("cycle");

        // Verify that the non-cyclic hierarchy works correctly
        assertThat(seniorReaches1)
                .as("Senior role must have all junior role permissions in acyclic hierarchy")
                .containsAll(juniorReaches1);

        assertThat(seniorReaches1.size())
                .as("Senior role should have more permissions than junior")
                .isGreaterThan(juniorReaches1.size());
    }

    // MR-INH-03: path independence property
    @Property(tries = 150)
    @Label("INH-03: Multiple inheritance paths should yield consistent permissions")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "INH-03",
            description = "Role A should be able to reach D through the remaining path, after one path is removed"
    )
    void multiplePathsYieldSamePermissions() {
        _r("INH-03");
        Map<String, List<String>> diamondHierarchy = new HashMap<>();
        diamondHierarchy.put("ROLE_A", Arrays.asList("ROLE_B", "ROLE_C"));
        diamondHierarchy.put("ROLE_B", Collections.singletonList("ROLE_D"));
        diamondHierarchy.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(diamondHierarchy));

        Set<String> reachableWithBothPaths = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_A");

        // follow up: remove one path (A > C > D)
        Map<String, List<String>> singlePathHierarchy = new HashMap<>();
        singlePathHierarchy.put("ROLE_A", Collections.singletonList("ROLE_B"));
        singlePathHierarchy.put("ROLE_B", Collections.singletonList("ROLE_D"));
        singlePathHierarchy.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(singlePathHierarchy));

        Set<String> reachableWithOnePath = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_A");

        // MR: A should still reach D with one path removed
        assertThat(reachableWithOnePath)
                .as("Role A should reach D through remaining path")
                .contains("ROLE_D");

        // with one path left, shouldn't reach C anymore
        assertThat(reachableWithOnePath)
                .as("Role A shouldn't reach C when path is removed")
                .doesNotContain("ROLE_C");
    _pass();
    }

    // HMR-INH-04: hierarchy extension property
    @Property(tries = 150)
    @Label("INH-04: Extending hierarchy shouldn't remove existing permissions")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "INH-04",
            description = "HMR: extending the hierarchy by adding new roles should not remove the existing reachability"
    )
    void extendingHierarchyPreservesExistingPermissions(
            @ForAll @IntRange(min = 2, max = 4) int initialSize,
            @ForAll @IntRange(min = 1, max = 3) int extensionSize) {
        _r("INH-04");

        // source: create initial hierarchy
        Map<String, List<String>> initialHierarchy = new HashMap<>();
        List<String> initialRoles = new ArrayList<>();

        for (int i = 0; i < initialSize; i++) {
            initialRoles.add("ROLE_INITIAL_" + i);
        }

        for (int i = 0; i < initialRoles.size() - 1; i++) {
            initialHierarchy.put(initialRoles.get(i),
                    Collections.singletonList(initialRoles.get(i + 1)));
        }

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(initialHierarchy));

        // record all reachability relationships in initial hierarchy
        Map<String, Set<String>> initialReachability = new HashMap<>();
        for (String role : initialRoles) {
            initialReachability.put(role, SpringSecurityTestHelpers.getReachableAuthorities(impl1, role));
        }

        // follow-up: extend hierarchy with new roles
        Map<String, List<String>> extendedHierarchy = new HashMap<>(initialHierarchy);
        String prevRole = initialRoles.get(initialRoles.size() - 1);
        for (int i = 0; i < extensionSize; i++) {
            String newRole = "ROLE_NEW_" + i;
            extendedHierarchy.put(prevRole, Collections.singletonList(newRole));
            prevRole = newRole;
        }

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(extendedHierarchy));

        // MR: all initial reachability must be preserved
        for (String role : initialRoles) {
            Set<String> newReachability = SpringSecurityTestHelpers.getReachableAuthorities(impl2, role);
            assertThat(newReachability)
                    .as("Role %s should not lose permissions", role)
                    .containsAll(initialReachability.get(role));
        }
    _pass();
    }

    // MR-INH-05: role removal impact property
    @Property(tries = 150)
    @Label("INH-05: Removing role should only affect its dependents")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "INH-05",
            description = "Removing ROLE_C should only affect C's dependents, the branch in ROLE_D should remain unchanged"
    )
    void roleRemovalAffectsOnlyDependents() {
        _r("INH-05");
        Map<String, List<String>> fullHierarchy = new HashMap<>();
        fullHierarchy.put("ROLE_A", Collections.singletonList("ROLE_B"));
        fullHierarchy.put("ROLE_B", Arrays.asList("ROLE_C", "ROLE_D"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(fullHierarchy));

        Set<String> aReachBefore = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_A");
        Set<String> bReachBefore = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_B");

        // follow up: remove ROLE_C from the hierarchy
        Map<String, List<String>> reducedHierarchy = new HashMap<>();
        reducedHierarchy.put("ROLE_A", Collections.singletonList("ROLE_B"));
        reducedHierarchy.put("ROLE_B", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(reducedHierarchy));

        Set<String> aReachAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_A");
        Set<String> bReachAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_B");

        // the MRs:
        // 1. A and B should still reach D (unaffected branch)
        assertThat(aReachAfter).contains("ROLE_D");
        assertThat(bReachAfter).contains("ROLE_D");

        // 2. A and B should no longer reach C
        assertThat(aReachAfter).doesNotContain("ROLE_C");
        assertThat(bReachAfter).doesNotContain("ROLE_C");

        // 3. number of lost permission should be exactly one
        assertThat(aReachBefore.size() - aReachAfter.size())
                .as("Role A should lose exactly one permission (ROLE_C)")
                .isEqualTo(1);
    _pass();
    }

    // MR-INH-06: multi-hop permission propagation
    @Property(tries = 150)
    @Label("INH-06: Multi-hop transitive inheritance must propagate all permissions")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "INH-06",
            description = "The top tier should contain all permissions from the middle and bottom tier"
    )
    void multiHopPermissionPropagation(@ForAll @IntRange(min = 3, max = 6) int depth) {
        _r("INH-06");
        // source: creating a 3 level hierarchy H1 > H2 > H3
        Map<String, List<String>> hierarchy = new HashMap<>();
        List<String> roles = new ArrayList<>();

        for (int i = 0; i < depth; i++) {
            roles.add("ROLE_TIER_" + i);
        }

        for (int i = 0; i < roles.size() - 1; i++) {
            hierarchy.put(roles.get(i), Collections.singletonList(roles.get(i + 1)));
        }

        RoleHierarchyImpl impl = new RoleHierarchyImpl();
        impl.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(hierarchy));

        // follow up: top role should have permissions from all levels
        String topRole = roles.get(0);
        String middleRole = roles.get(roles.size() / 2);
        String bottomRole = roles.get(roles.size() - 1);

        Set<String> topPerms = SpringSecurityTestHelpers.getReachableAuthorities(impl, topRole);
        Set<String> middlePerms = SpringSecurityTestHelpers.getReachableAuthorities(impl, middleRole);
        Set<String> bottomPerms = SpringSecurityTestHelpers.getReachableAuthorities(impl, bottomRole);

        // MR: top should have union of all descendant permissions
        assertThat(topPerms).as("Top role must have all permissions from middle tier")
                .containsAll(middlePerms);
        assertThat(middlePerms).as("Top role must have all permissions from bottom tier")
                .containsAll(bottomPerms);
        assertThat(topPerms).as("Top role should reach all %d roles transitively", depth)
                .hasSize(depth);
    _pass();
    }

    // HMR-INH-07: Diamond Inheritance Consistency
    @Property(tries = 150)
    @Label("INH-07: Diamond pattern inheritance should not create any duplicates")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "INH-07",
            description = "HMR: Diamond pattern must not duplicate permissions (Role D should only appear once) "
    )
    void diamondInheritanceConsistency(){
        _r("INH-07");
        // source: create diamond hierarchy
        Map<String, List<String>> diamondHierarchy = new HashMap<>();
        diamondHierarchy.put("ROLE_A", Arrays.asList("ROLE_B", "ROLE_C"));
        diamondHierarchy.put("ROLE_B", Collections.singletonList("ROLE_D"));
        diamondHierarchy.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(diamondHierarchy));

        Set<String> topReach = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_A");

        // follow-up: comparing the linear chain giving the same permission
        Map<String, List<String>> linear = new HashMap<>();
        linear.put("ROLE_A", Collections.singletonList("ROLE_B"));
        linear.put("ROLE_B", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(linear));

        Set<String> linearReach = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_A");

        // MR: diamond should not create permission duplication
        assertThat(topReach).as("Diamond pattern should have all 4 unique roles")
                .hasSize(4)
                .contains("ROLE_A", "ROLE_B", "ROLE_C", "ROLE_D");

        // both should reach the bottom
        assertThat(topReach).contains("ROLE_D");
        assertThat(linearReach).contains("ROLE_D");
    _pass();
    }

    // MR-INH-08: permission addition propagation
    @Property(tries = 150)
    @Label("INH-08: Adding permission to junior role must propagate to all seniors")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "INH-08",
            description = "Adding permission to junior must also affect the senior's permission count"
    )
    void permissionAdditionPropagates() {
        _r("INH-08");
        // source: initial hierarchy without new permission
        Map<String, List<String>> initial = new HashMap<>();
        initial.put("ROLE_SENIOR", Collections.singletonList("ROLE_JUNIOR"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(initial));

        Set<String> seniorBefore = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_SENIOR");

        // follow-up: add new role beneath junior
        Map<String, List<String> > extended = new HashMap<>();
        extended.put("ROLE_SENIOR", Collections.singletonList("ROLE_JUNIOR"));
        extended.put("ROLE_JUNIOR", Collections.singletonList("ROLE_NEW_PERMISSION"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(extended));

        Set<String> seniorAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_SENIOR");
        Set<String> juniorAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_JUNIOR");

        // MR: senior must gain the new permission
        assertThat(seniorAfter).as("senior role must inherit new permission added to junior")
                .contains("ROLE_NEW_PERMISSION");
        assertThat(juniorAfter).as("junior role must have new permission")
                .contains("ROLE_NEW_PERMISSION");
        assertThat(seniorAfter.size()).as("senior should have one more permission than before")
                .isEqualTo(seniorBefore.size() + 1);
    _pass();
    }

    // MR-INH-09: Sibling Role Independence
    @Property(tries = 150)
    @Label("INH-09: Sibling roles at same level should not affect each other")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "INH-09",
            description = "Modifying chain B should not affect chain A"
    )
    void siblingRoleIndependence(){
        _r("INH-09");
        // source: create two independent chains
        Map<String, List<String>> parallel = new HashMap<>();
        parallel.put("ROLE_CHAIN_A_TOP", Collections.singletonList("ROLE_CHAIN_A_BOTTOM"));
        parallel.put("ROLE_CHAIN_B_TOP", Collections.singletonList("ROLE_CHAIN_B_BOTTOM"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(parallel));

        Set<String> chainABefore = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_CHAIN_A_TOP");

        // follow-up: modify the B chain only
        Map<String, List<String>> modifiedB = new HashMap<>();
        modifiedB.put("ROLE_CHAIN_A_TOP", Collections.singletonList("ROLE_CHAIN_A_BOTTOM"));
        modifiedB.put("ROLE_CHAIN_B_TOP", Collections.singletonList("ROLE_CHAIN_B_BOTTOM"));
        modifiedB.put("ROLE_CHAIN_B_BOTTOM", Collections.singletonList("ROLE_CHAIN_B_EXTRA"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(modifiedB));

        Set<String> chainAAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_CHAIN_A_TOP");
        Set<String> chainBAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_CHAIN_B_TOP");

        // MR: chain A should be completely unaffected
        assertThat(chainAAfter).as("Chain A permissions must remain identical when Chain B is modified")
                .isEqualTo(chainABefore);

        // chain B should have gained permission
        assertThat(chainBAfter).as("Chain B should have new permissions")
                .contains("ROLE_CHAIN_B_EXTRA");

        // chains shouldn't overlap
        assertThat(chainAAfter).as("Chain A should not gain permissions from Chain B")
                .doesNotContain("ROLE_CHAIN_B_EXTRA", "ROLE_CHAIN_B_BOTTOM", "ROLE_CHAIN_B_TOP");
    _pass();
    }

    // MR-INH-10: Inheritance Path Symmetry
    @Property(tries = 150)
    @Label("INH-10: Multiple paths to same permission must be consistent")
    @BugType(
            category = BugType.MRCategory.INHERITANCE,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "INH-10",
            description = "Shared permission should only appear once regardless multiple paths that leads to it"
    )
    void inheritancePathSymmetry() {
        _r("INH-10");
        // source: create roles with two parent paths to the same permissions
        Map<String, List<String>> dualPath = new HashMap<>();
        dualPath.put("ROLE_TARGET", Arrays.asList("ROLE_PATH1", "ROLE_PATH2"));
        dualPath.put("ROLE_PATH1", Collections.singletonList("ROLE_SHARED_PERMISSION"));
        dualPath.put("ROLE_PATH2", Collections.singletonList("ROLE_SHARED_PERMISSION"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(dualPath));

        Set<String> targetPerms = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_TARGET");

        // follow-up: remove one path and check if the permission still exists
        Map<String, List<String>> singlePath = new HashMap<>();
        singlePath.put("ROLE_TARGET", Collections.singletonList("ROLE_PATH1"));
        singlePath.put("ROLE_PATH1", Collections.singletonList("ROLE_SHARED_PERMISSION"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(singlePath));

        Set<String> targetPermsSingle = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_TARGET");

        // MR: shared permission should exist in both configuration
        assertThat(targetPerms).as("Target should reach shared permission through multiple paths")
                .contains("ROLE_SHARED_PERMISSION");
        assertThat(targetPermsSingle).as("Target should still reach shared permission through a single path")
                .contains("ROLE_SHARED_PERMISSION");

        // permission should not have any duplicates
        long countInDual = targetPerms.stream()
                .filter(p -> p.equals("ROLE_SHARED_PERMISSION"))
                .count();

        assertThat(countInDual).as("Shared permission should appear exactly once despite multiple paths")
                .isEqualTo(1);
    _pass();
    }

}