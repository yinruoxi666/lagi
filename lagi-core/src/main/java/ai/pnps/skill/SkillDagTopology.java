package ai.pnps.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * DAG 校验与拓扑排序（对齐 ms-agent {@code DAGExecutor} / {@code AutoSkills._topological_sort_dag} 行为）。
 * <p>
 * 约定：{@code dag.get(node)} 为 node 所依赖的 skillId 列表（先执行依赖再执行 node）。
 */
public final class SkillDagTopology {

    private static final Logger log = LoggerFactory.getLogger(SkillDagTopology.class);

    private SkillDagTopology() {
    }

    /**
     * 依赖先出队：返回顺序满足所有边 dep → node（dep 在 node 前）。
     */
    public static List<String> topologicalSort(Map<String, List<String>> dag) {
        if (dag == null || dag.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> nodes = new LinkedHashSet<>(dag.keySet());
        for (List<String> deps : dag.values()) {
            if (deps != null) {
                for (String d : deps) {
                    nodes.add(d);
                }
            }
        }
        Map<String, Integer> inDegree = new HashMap<>();
        for (String n : nodes) {
            List<String> deps = dag.get(n);
            inDegree.put(n, deps != null ? deps.size() : 0);
        }
        TreeSet<String> queue = new TreeSet<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.pollFirst();
            result.add(node);
            for (Map.Entry<String, List<String>> e : dag.entrySet()) {
                List<String> deps = e.getValue();
                if (deps != null && deps.contains(node)) {
                    String other = e.getKey();
                    int deg = inDegree.getOrDefault(other, 0) - 1;
                    inDegree.put(other, deg);
                    if (deg == 0) {
                        queue.add(other);
                    }
                }
            }
        }
        if (result.size() < nodes.size()) {
            Set<String> remaining = new HashSet<>(nodes);
            remaining.removeAll(result);
            log.warn("Topological sort incomplete (possible cycle), appending remaining: {}", remaining);
            List<String> tail = new ArrayList<>(remaining);
            Collections.sort(tail);
            result.addAll(tail);
        }
        return result;
    }
}
