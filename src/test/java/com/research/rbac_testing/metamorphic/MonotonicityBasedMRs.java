package com.research.rbac_testing.metamorphic;

import com.research.rbac_testing.annotations.BugType;
import com.research.rbac_testing.helpers.SpringSecurityTestHelpers;
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
 * Monotonicity-Based Metamorphic Relations
 *
 * MRs (Bug-Finding): MON-01, MON-02, MON-04, MON-06, MON-07, MON-08, MON-10
 * HMRs (Exploratory): MON-03, MON-05, MON-09
 *
 * Classification based on Ying et al. (2025) framework
 */

public class MonotonicityBasedMRs {

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
    // MR-MON-01: upward monotonicity property
    @Property(tries = 150)
    @Label("MON-01: Parent roles must have >= permissions of child roles")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "MON-01",
            description = "For every adjacent pair in chain, parent must contain all child's reachable permissions"
    )
    void parentHasAtLeastChildPermissions(@ForAll @IntRange(min = 2, max = 6) int hierarchyDepth) {
        _r("MON-01");

        // source: building a multi-level hierarchy
        Map<String, List<String>> hierarchy = new HashMap<>();
        List<String> roles = new ArrayList<>();

        for (int i = 0; i < hierarchyDepth; i++) {
            roles.add("ROLE_LEVEL_" + i);
        }

        // follow-up: creating chain with permissions flowing down
        for (int i = 0; i < roles.size() - 1; i++) {
            hierarchy.put(roles.get(i), Collections.singletonList(roles.get(i + 1)));
        }

        RoleHierarchyImpl impl = new RoleHierarchyImpl();
        impl.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(hierarchy));

        // MR: each parent must have all child's permissions
        for (int i = 0; i < roles.size() - 1; i++) {
            String parent = roles.get(i);
            String child = roles.get(i + 1);

            Set<String> parentPerms = SpringSecurityTestHelpers.getReachableAuthorities(impl, parent);
            Set<String> childPerms = SpringSecurityTestHelpers.getReachableAuthorities(impl, child);

            assertThat(parentPerms)
                    .as("Parent %s must have all permissions of child %s", parent, child)
                    .containsAll(childPerms);
        }
    _pass();
    }

    // MR-MON-02: permission addition monotonicity
    @Property(tries = 150)
    @Label("MON-02: Adding inheritance edges should not reduce any role's permissions")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "MON-02",
            description = "Connecting two separate chains with a new edge should not remove any existing reachability"
    )
    void addingEdgesNeverReducesPermissions() {
        _r("MON-02");
        // source: the initial simple hierarchy
        Map<String, List<String>> initial = new HashMap<>();
        initial.put("ROLE_A", Collections.singletonList("ROLE_B"));
        initial.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(initial));

        Map<String, Set<String>> initialPerms = new HashMap<>();
        for (String role : Arrays.asList("ROLE_A", "ROLE_B", "ROLE_C", "ROLE_D")) {
            initialPerms.put(role,
                    SpringSecurityTestHelpers.getReachableAuthorities(impl1, role));
        }

        // follow-up: Add edge connecting the two chains (B > C)
        Map<String, List<String>> extended = new HashMap<>();
        extended.put("ROLE_A", Collections.singletonList("ROLE_B"));
        extended.put("ROLE_B", Collections.singletonList("ROLE_C")); // New edge!
        extended.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(extended));

        // MR: no role should lose permissions
        for (String role : Arrays.asList("ROLE_A", "ROLE_B", "ROLE_C", "ROLE_D")) {
            Set<String> newPerms = SpringSecurityTestHelpers.getReachableAuthorities(impl2, role);
            assertThat(newPerms)
                    .as("Role %s should not lose permissions when new edge added", role)
                    .containsAll(initialPerms.get(role));
        }

        // additionally, A and B should have gained permissions
        Set<String> aNew = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_A");
        Set<String> bNew = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_B");

        assertThat(aNew).contains("ROLE_C", "ROLE_D");
        assertThat(bNew).contains("ROLE_C", "ROLE_D");
    _pass();
    }

    // HMR-MON-03: hierarchy Strengthening Property
    @Property(tries = 150)
    @Label("MON-03: Strengthening hierarchy increases reachability monotonically")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "MON-03",
            description = "HMR: adding cross-connections to a sparse hierarchy must result in >= reachability for all roles"
    )
    void strengtheningHierarchyIncreasesReachability() {
        _r("MON-03");
        // source: sparse hierarchy (minimal connections)
        Map<String, List<String>> sparse = new HashMap<>();
        sparse.put("ROLE_A", Collections.singletonList("ROLE_B"));
        sparse.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(sparse));

        int sparseReachA = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_A").size();
        int sparseReachC = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_C").size();

        // follow-up: dense hierarchy (add cross-connections)
        Map<String, List<String>> dense = new HashMap<>();
        dense.put("ROLE_A", Arrays.asList("ROLE_B", "ROLE_C")); // A now connects to C too
        dense.put("ROLE_B", Collections.singletonList("ROLE_D")); // B now connects to D
        dense.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(dense));

        int denseReachA = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_A").size();
        int denseReachC = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_C").size();

        // MR: dense hierarchy should have >= reachability
        assertThat(denseReachA)
                .as("Role A should reach more (or same) authorities in denser hierarchy")
                .isGreaterThanOrEqualTo(sparseReachA);

        Set<String> aReach = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_A");
        assertThat(aReach)
                .as("Role A in dense hierarchy should reach all roles")
                .containsExactlyInAnyOrder("ROLE_A", "ROLE_B", "ROLE_C", "ROLE_D");
    _pass();
    }

    // MR-MON-04: hierarchy weakening property
    @Property(tries = 150)
    @Label("MON-04: Removing edges should monotonically reduce reachability")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "MON-04",
            description = "After the edges removal from A, sparse reachability must be a subset of dense reachability (A should not reach C and D anymore)"
    )
    void weakeningHierarchyReducesReachability() {
        _r("MON-04");
        // source: dense hierarchy
        Map<String, List<String>> dense = new HashMap<>();
        dense.put("ROLE_A", Arrays.asList("ROLE_B", "ROLE_C", "ROLE_D"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(dense));

        Set<String> denseReach = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_A");

        // follow-up: remove some edges (keep only A > B)
        Map<String, List<String>> sparse = new HashMap<>();
        sparse.put("ROLE_A", Collections.singletonList("ROLE_B"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(sparse));

        Set<String> sparseReach = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_A");

        // MR: sparse reachability must be subset of dense
        assertThat(sparseReach.size())
                .as("Removing edges should reduce or maintain reachability count")
                .isLessThanOrEqualTo(denseReach.size());

        assertThat(denseReach)
                .as("Dense hierarchy permissions should include all sparse permissions")
                .containsAll(sparseReach);

        // sparse should not reach C and D anymore
        assertThat(sparseReach)
                .as("After edge removal, A should not reach C and D")
                .doesNotContain("ROLE_C", "ROLE_D");
    _pass();
    }

    // HMR-MON-05: subset consistency across hierarchy levels
    @Property(tries = 150)
    @Label("MON-05: Reachability sets must maintain subset relationship across levels")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "MON-05",
            description = "HMR: for every adjacent level pair, senior reachability must strictly contain junior reachability, and top must reach all roles"
    )
    void reachabilitySubsetConsistencyAcrossLevels(
            @ForAll @IntRange(min = 4, max = 7) int levels) {
        _r("MON-05");

        // source: build deep hierarchy
        Map<String, List<String>> hierarchy = new HashMap<>();
        List<String> roles = new ArrayList<>();

        for (int i = 0; i < levels; i++) {
            roles.add("ROLE_L" + i);
        }

        for (int i = 0; i < roles.size() - 1; i++) {
            hierarchy.put(roles.get(i), Collections.singletonList(roles.get(i + 1)));
        }

        RoleHierarchyImpl impl = new RoleHierarchyImpl();
        impl.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(hierarchy));

        // get reachability sets for all roles
        Map<String, Set<String>> reachability = new HashMap<>();
        for (String role : roles) {
            reachability.put(role,
                    SpringSecurityTestHelpers.getReachableAuthorities(impl, role));
        }

        // MR: for all adjacent levels i < j,
        // reach(role_j) ⊆ reach(role_i)
        for (int i = 0; i < roles.size() - 1; i++) {
            String senior = roles.get(i);
            String junior = roles.get(i + 1);

            Set<String> seniorReach = reachability.get(senior);
            Set<String> juniorReach = reachability.get(junior);

            assertThat(seniorReach)
                    .as("Senior role %s must have all permissions of junior role %s", senior, junior)
                    .containsAll(juniorReach);

            // senior should have strictly MORE permissions (include itself)
            assertThat(seniorReach.size())
                    .as("Senior role should reach at least as many authorities as junior")
                    .isGreaterThanOrEqualTo(juniorReach.size());
        }

        // check transitive property: reach(L0) should include all roles
        String topRole = roles.get(0);
        Set<String> topReach = reachability.get(topRole);

        assertThat(topReach)
                .as("Top role should transitively reach all roles in hierarchy")
                .containsAll(roles);
    _pass();
    }

    // MR-MON-06: role removal constraint
    @Property(tries = 150)
    @Label("MON-06: removing a role should never increase any role's permissions")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "MON-06",
            description = "Removing ROLE_C from chain A>B>C>D should not cause any remaining role to gain permissions"
    )
    void roleRemovalNeverIncreasesPermissions() {
        _r("MON-06");
        // source: full hierarchy A > B > C > D
        Map<String, List<String>> full = new HashMap<>();
        full.put("ROLE_A", Collections.singletonList("ROLE_B"));
        full.put("ROLE_B", Collections.singletonList("ROLE_C"));
        full.put("ROLE_C", Collections.singletonList("ROLE_D"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(full));

        Map<String, Set<String>> beforeRemoval = new HashMap<>();
        for (String role : Arrays.asList("ROLE_A", "ROLE_B", "ROLE_C", "ROLE_D")) {
            beforeRemoval.put(role, SpringSecurityTestHelpers.getReachableAuthorities(impl1, role));
        }

        // follow-up: remove ROLE_C from the hierarchy
        Map<String, List<String>> reduced = new HashMap<>();
        reduced.put("ROLE_A", Collections.singletonList("ROLE_B"));
        reduced.put("ROLE_B", Collections.singletonList("ROLE_D")); // skip C

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(reduced));

        Set<String> aAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_A");
        Set<String> bAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_B");
        Set<String> dAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_D");

        // MR: no role should gain permissions from removal
        assertThat(aAfter.size()).as("Role A should not gain permissions after role removal")
                .isLessThanOrEqualTo(beforeRemoval.get("ROLE_A").size());

        assertThat(bAfter.size()).as("Role B should not gain permissions after role removal")
                .isLessThanOrEqualTo(beforeRemoval.get("ROLE_B").size());

        assertThat(dAfter.size()).as("Role D permissions should be unchanged")
                .isEqualTo(beforeRemoval.get("ROLE_D").size());
    _pass();
    }

    // MR-MON-07: permission revocation monotonicity
    @Property(tries = 150)
    @Label("MON-07: revoking permission should only decrease system-wide access")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "MON-07",
            description = "Removing ROLE_AUDIT must reduce total system-wide permissions and ADMIN must no longer reach AUDIT"
    )
    void permissionRevocationMonotonicity() {
        _r("MON-07");
        // source: hierarchy with multiple permissions
        Map<String, List<String>> full = new HashMap<>();
        full.put("ROLE_ADMIN", Arrays.asList("ROLE_USER","ROLE_AUDIT"));
        full.put("ROLE_USER", Collections.singletonList("ROLE_GUEST"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(full));

        int totalPermsBefore = 0;
        for (String role : Arrays.asList("ROLE_ADMIN", "ROLE_USER", "ROLE_AUDIT" ,"ROLE_GUEST")) {
            totalPermsBefore += SpringSecurityTestHelpers.getReachableAuthorities(impl1, role).size();
        }

        // follow-up: revoke one permission (remove ROLE_AUDIT)
        Map<String, List<String>> reduced = new HashMap<>();
        reduced.put("ROLE_ADMIN", Collections.singletonList("ROLE_USER"));
        reduced.put("ROLE_USER", Collections.singletonList("ROLE_GUEST"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(reduced));

        int totalPermsAfter = 0;
        for (String role : Arrays.asList("ROLE_ADMIN", "ROLE_USER", "ROLE_AUDIT", "ROLE_GUEST")) {
            totalPermsAfter += SpringSecurityTestHelpers.getReachableAuthorities(impl2, role).size();
        }

        // MR: total permissions should decrease
        assertThat(totalPermsAfter).as("Total perms after role removal should decrease")
                .isLessThan(totalPermsBefore);

        Set<String> adminAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_ADMIN");
        assertThat(adminAfter).as("ADMIN should no longer reach revoked AUDIT permission")
                .doesNotContain("ROLE_AUDIT");
    _pass();
    }

    // MR-MON-08: hierarchy Depth Monotonicity
    @Property(tries = 150)
    @Label("MON-08: Adding hierarchy depth should monotonically increase top-level permissions")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "MON-08",
            description = "Adding addedDepth new roles below the bottom of a shallow chain must increase top reachability by exactly addedDepth"
    )
    void hierarchyDepthMonotonicity(@ForAll @IntRange(min = 2, max = 4) int initialDepth,
                                    @ForAll @IntRange(min = 1, max = 3) int addedDepth) {
        _r("MON-08");
        // source: shallow hierarchy
        Map<String, List<String>> shallow = new HashMap<>();
        List<String> shallowRoles = new ArrayList<>();

        for (int i = 0; i < initialDepth; i++) {
            shallowRoles.add("ROLE_SHALLOW_" + i);
        }

        for (int i = 0; i < shallowRoles.size() - 1; i++) {
            shallow.put(shallowRoles.get(i), Collections.singletonList(shallowRoles.get(i + 1)));
        }

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(shallow));

        Set<String> topBefore = SpringSecurityTestHelpers.getReachableAuthorities(impl1, shallowRoles.get(0));

        // follow-up: add more depth to hierarchy
        Map<String, List<String>> deeper = new HashMap<>(shallow);
        String lastShallow = shallowRoles.get(shallowRoles.size() - 1);

        for (int i = 0; i < addedDepth; i++) {
            String newRole = "ROLE_DEEP_" + i;
            if (i == 0) {
                deeper.put(lastShallow, Collections.singletonList(newRole));
            } else {
                deeper.put("ROLE_DEEP_" + (i - 1), Collections.singletonList(newRole));
            }
        }

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(deeper));

        Set<String> topAfter = SpringSecurityTestHelpers.getReachableAuthorities(impl2, shallowRoles.get(0));

        // MR: top level should have all previous permissions plus new ones
        assertThat(topAfter)
                .as("Top role must retain all original permissions")
                .containsAll(topBefore);

        assertThat(topAfter.size())
                .as("Top role should gain %d new permissions", addedDepth)
                .isEqualTo(topBefore.size() + addedDepth);
    _pass();
    }

    // HMR-MON-09: user assignment monotonicity
    @Property(tries = 150)
    @Label("MON-09: Assigning user to additional roles should never decrease permissions")
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS},
            mrId = "MON-09",
            description = "HMR: a combined permissions from two separate roles must be the union of both (no permission from either role is lost)"
    )
    void userAssignmentMonotonicity() {
        _r("MON-09");
        // source: user assigned to single role
        Map<String, List<String>> singleRole = new HashMap<>();
        singleRole.put("ROLE_BASIC", Collections.singletonList("ROLE_READ"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(singleRole));

        Set<String> basicPerms = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_BASIC");

        // follow-up: assign user to additional role (simulate multi-role assignment)
        Map<String, List<String>> multiRole = new HashMap<>();
        multiRole.put("ROLE_BASIC", Collections.singletonList("ROLE_READ"));
        multiRole.put("ROLE_EXTRA", Collections.singletonList("ROLE_WRITE"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(multiRole));

        Set<String> extraPerms = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_EXTRA");

        // MR: combined permissions should be union (monotonic increase)
        Set<String> combinedPerms = new HashSet<>();
        combinedPerms.addAll(basicPerms);
        combinedPerms.addAll(extraPerms);

        assertThat(combinedPerms.size())
                .as("Multi-role assignment should have union of permissions")
                .isGreaterThanOrEqualTo(basicPerms.size());

        assertThat(combinedPerms.size())
                .as("Multi-role assignment should have union of permissions")
                .isGreaterThanOrEqualTo(extraPerms.size());

        // verify no permission loss
        assertThat(combinedPerms)
                .as("Combined permissions must include all basic permissions")
                .containsAll(basicPerms);
    _pass();
    }

    // MR-MON-10: cumulative permission addition
    @Property(tries = 150)
    @BugType(
            category = BugType.MRCategory.MONOTONICITY,
            bugTypes = {BugType.BugTypeEnum.PERMISSION_LOSS, BugType.BugTypeEnum.PRIVILEGE_LEAKAGE},
            mrId = "MON-10",
            description = "Adding permissions one at a time sequentially must be producing the same final result as adding all three at once in a batch"
    )
    @Label("MON-10: Sequential vs batch permission addition should yield same result")
    void cumulativePermissionAddition() {
        _r("MON-10");
        // source: add permissions one by one
        Map<String, List<String>> step1 = new HashMap<>();
        step1.put("ROLE_ROOT", Collections.singletonList("ROLE_PERM1"));

        RoleHierarchyImpl impl1 = new RoleHierarchyImpl();
        impl1.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(step1));

        Map<String, List<String>> step2 = new HashMap<>();
        step2.put("ROLE_ROOT", Arrays.asList("ROLE_PERM1", "ROLE_PERM2"));

        RoleHierarchyImpl impl2 = new RoleHierarchyImpl();
        impl2.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(step2));

        Map<String, List<String>> step3 = new HashMap<>();
        step3.put("ROLE_ROOT", Arrays.asList("ROLE_PERM1", "ROLE_PERM2", "ROLE_PERM3"));

        RoleHierarchyImpl impl3 = new RoleHierarchyImpl();
        impl3.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(step3));

        // follow-up: add all permissions at once
        Map<String, List<String>> batch = new HashMap<>();
        batch.put("ROLE_ROOT", Arrays.asList("ROLE_PERM1", "ROLE_PERM2", "ROLE_PERM3"));

        RoleHierarchyImpl implBatch = new RoleHierarchyImpl();
        implBatch.setHierarchy(SpringSecurityTestHelpers.buildHierarchyString(batch));

        Set<String> sequentialFinal = SpringSecurityTestHelpers.getReachableAuthorities(impl3, "ROLE_ROOT");
        Set<String> batchResult = SpringSecurityTestHelpers.getReachableAuthorities(implBatch, "ROLE_ROOT");

        // MR: sequential and batch should produce identical results
        assertThat(batchResult)
                .as("Batch addition should equal sequential addition final state")
                .isEqualTo(sequentialFinal);

        assertThat(batchResult)
                .as("Should have all 4 permissions (root + 3 perms)")
                .hasSize(4)
                .contains("ROLE_ROOT", "ROLE_PERM1", "ROLE_PERM2", "ROLE_PERM3");

        // verify monotonic growth in sequential
        Set<String> seq1 = SpringSecurityTestHelpers.getReachableAuthorities(impl1, "ROLE_ROOT");
        Set<String> seq2 = SpringSecurityTestHelpers.getReachableAuthorities(impl2, "ROLE_ROOT");

        assertThat(seq2.size())
                .as("Step 2 should have more permissions than step 1")
                .isGreaterThan(seq1.size());

        assertThat(sequentialFinal.size())
                .as("Final step should have more permissions than step 2")
                .isGreaterThan(seq2.size());
    _pass();
    }

}