package org.kpush88.Thermal_2.v4;

import java.util.*;

public class SchemeGraph <T> {

    // вершина -> множество исходящих соседей (рёбра from -> to)
    private final Map<T, Set<T>> outputList = new HashMap<>();
    // вершина -> множество входящих соседей (рёбра to -> from), для удобства
    private final Map<T, Set<T>> inputList = new HashMap<>();

    public void addConnection(T connection) {
        outputList.putIfAbsent(connection, new HashSet<>());
        inputList.putIfAbsent(connection, new HashSet<>());
    }

    /** Добавляет направленное ребро from -> to (только в одну сторону!) */
    public void addEdge(T from, T to) {
        addConnection(from);
        addConnection(to);
        outputList.get(from).add(to);
        inputList.get(to).add(from);
    }

    public void removeEdge(T from, T to) {
        if (outputList.containsKey(from)) {
            outputList.get(from).remove(to);
        }
        if (inputList.containsKey(to)) {
            inputList.get(to).remove(from);
        }
    }

    /** Исходящие соседи (куда идут рёбра из connection) */
    public Set<T> getOutgoing(T connection) {
        return outputList.getOrDefault(connection, Collections.emptySet());
    }

    /** Входящие соседи (откуда идут рёбра в connection) */
    public Set<T> getIncoming(T connection) {
        return inputList.getOrDefault(connection, Collections.emptySet());
    }

    public Set<T> getVertices() {
        return outputList.keySet();
    }

    /** Степень вершины: [входящая, исходящая] */
    public int[] degree(T connection) {
        return new int[]{getIncoming(connection).size(), getOutgoing(connection).size()};
    }

    // ---------- Обходы ----------

    /** BFS по направлению рёбер */
    public List<T> bfs(T start) {
        List<T> result = new ArrayList<>();
        Set<T> visited = new HashSet<>();
        Queue<T> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            T current = queue.poll();
            result.add(current);
            for (T neighbor : getOutgoing(current)) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return result;
    }

    /** DFS по направлению рёбер */
    public List<T> dfs(T start) {
        List<T> result = new ArrayList<>();
        dfsHelper(start, new HashSet<>(), result);
        return result;
    }

    private void dfsHelper(T vertex, Set<T> visited, List<T> result) {
        if (!visited.add(vertex)) return;
        result.add(vertex);
        for (T neighbor : getOutgoing(vertex)) {
            dfsHelper(neighbor, visited, result);
        }
    }



}
