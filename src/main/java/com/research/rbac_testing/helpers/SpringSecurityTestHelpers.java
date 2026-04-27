package com.research.rbac_testing.helpers;

import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class SpringSecurityTestHelpers {

    // a helper utility to test spring security's RoleHierarchyImpl
    // uses reflection to access the internal state and verify the MRs

    // creating a hierarchy string in spring security format
    public static String buildHierarchyString(Map<String, List<String>> parentChildMap) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, List<String>> entry : parentChildMap.entrySet()) {
            String parent = entry.getKey();
            for (String child : entry.getValue()) {
                if (!first) sb.append("\n");
                sb.append(parent).append(" > ").append(child);
                first = false;
            }
        }

        return sb.toString();
    }

    // access all reachable authorities from a given role
    public static Set<String> getReachableAuthorities(RoleHierarchyImpl hierarchy, String roleStr) {
        GrantedAuthority role = new SimpleGrantedAuthority(roleStr);
        Collection<? extends GrantedAuthority> reachable = hierarchy.getReachableGrantedAuthorities(
                Collections.singletonList(role)
        );

        return reachable.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    // uses reflection to access spring security's internal role map
    @SuppressWarnings("unchecked")
    public static Map<String, Set<String>> getInternalRoleMap(RoleHierarchyImpl hierarchy) {
        try {
            Field rolesReachableInOneStepMap = RoleHierarchyImpl.class.getDeclaredField("rolesReachableInOneStepMap");
            rolesReachableInOneStepMap.setAccessible(true);

            Map<String, Set<GrantedAuthority>> internalMap =
                    (Map<String, Set<GrantedAuthority>>) rolesReachableInOneStepMap.get(hierarchy);

            // converting to string-based map for easier testing
            Map<String, Set<String>> result = new HashMap<>();
            for (Map.Entry<String, Set<GrantedAuthority>> entry : internalMap.entrySet()) {
                Set<String> authorities = entry.getValue()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                result.put(entry.getKey(), authorities);
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException("failed to access the internal spring security state", e);
        }
    }

    // check if two role hierarchies are structurally equivalent
    public static boolean areHierarchiesEquivalent(RoleHierarchyImpl hierarchy1, RoleHierarchyImpl hierarchy2, List<String> testRoles) {
        for (String role: testRoles) {
            Set<String> reachable1 = getReachableAuthorities(hierarchy1, role);
            Set<String> reachable2 = getReachableAuthorities(hierarchy2, role);

            if (!reachable1.equals(reachable2)) {
                return false;
            }
        }

        return true;
    }

    // calculate the hierarchy depth
    public static int calculateDepth(Map<String, Set<String>> adjacencyMap) {
        Map<String, Integer> depths = new HashMap<>();

        // find roots (roles with no parents)
        Set<String> allRoles = new HashSet<>(adjacencyMap.keySet());
        Set<String> children = adjacencyMap.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        Set<String> roots = new HashSet<>(allRoles);
        roots.removeAll(children);

        // using BFS to calculate depths
        Queue<String> queue = new LinkedList<>(roots);
        roots.forEach(r -> depths.put(r,0));

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.get(current);

            Set<String> directChildren = adjacencyMap.getOrDefault(current, Collections.emptySet());
            for (String child: directChildren) {
                if (!depths.containsKey(child) || depths.get(child) < currentDepth + 1) {
                    depths.put(child, currentDepth + 1);
                    queue.add(child);
                }
            }
        }

        return depths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    // detects cycles in the role hierarchy
    public static boolean hasCycle(Map<String, Set<String>> adjacencyMap) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String role: adjacencyMap.keySet()) {
            if (hasCycleDFS(role, adjacencyMap, visited, recursionStack)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasCycleDFS(String role, Map<String, Set<String>> adjacencyMap,
                                       Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(role)) {
            return true;
        }

        if (visited.contains(role)) {
            return false;
        }

        visited.add(role);
        recursionStack.add(role);

        Set<String> children = adjacencyMap.getOrDefault(role, Collections.emptySet());
        for (String child: children) {
            if (hasCycleDFS(child, adjacencyMap, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(role);
        return false;
    }

    // generate a random role hierarchy for property-based testing
    public static Map<String, List<String>> generateRandomHierarchy(Random random, int numRoles, int maxChildren) {
        Map<String, List<String>> hierarchy = new HashMap<>();
        List<String> roles = new ArrayList<>();

        for (int i = 0; i < numRoles; i++) {
            roles.add("ROLE_" + i);
        }

        for (int i = 0; i < numRoles - 1; i++) {
            int numChildren = random.nextInt(Math.min(maxChildren, numRoles - i - 1)) + 1;
            List<String> children = new ArrayList<>();

            for (int j = 0; j < numChildren && (i + j + 1) < numRoles; j++) {
                children.add(roles.get(i + j + 1));
            }

            if (!children.isEmpty()) {
                hierarchy.put(roles.get(i), children);
            }
        }

        return hierarchy;
    }

}
