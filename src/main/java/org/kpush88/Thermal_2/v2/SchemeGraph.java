package org.kpush88.Thermal_2.v2;

import java.util.*;

public class SchemeGraph<T> {

    // вершина -> множество исходящих соседей (рёбра from -> to)
    private final Map<T, Set<T>> outputList = new HashMap<>();
    // вершина -> множество входящих соседей (рёбра to -> from), для удобства
    private final Map<T, Set<T>> inputList = new HashMap<>();

    public void addVertex(T vertex) {
        outputList.putIfAbsent(vertex, new HashSet<>());
        inputList.putIfAbsent(vertex, new HashSet<>());
    }

    /** Добавляет направленное ребро from -> to (только в одну сторону!) */
    public void addEdge(T from, T to) {
        addVertex(from);
        addVertex(to);
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

    /** Исходящие соседи (куда идут рёбра из vertex) */
    public Set<T> getOutgoing(T vertex) {
        return outputList.getOrDefault(vertex, Collections.emptySet());
    }

    /** Входящие соседи (откуда идут рёбра в vertex) */
    public Set<T> getIncoming(T vertex) {
        return inputList.getOrDefault(vertex, Collections.emptySet());
    }

    public Set<T> getVertices() {
        return outputList.keySet();
    }

    /** Степень вершины: [входящая, исходящая] */
    public int[] degree(T vertex) {
        return new int[]{getIncoming(vertex).size(), getOutgoing(vertex).size()};
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

    // ---------- Проверка ацикличности + топологическая сортировка ----------

    /** Обнаружение цикла через цветовую раскраску (белый/серый/чёрный) */
    public boolean hasCycle() {
        Map<T, Integer> color = new HashMap<>(); // 0 - белый, 1 - серый, 2 - чёрный
        for (T v : getVertices()) color.put(v, 0);
        for (T v : getVertices()) {
            if (color.get(v) == 0 && hasCycleDfs(v, color)) return true;
        }
        return false;
    }

    private boolean hasCycleDfs(T v, Map<T, Integer> color) {
        color.put(v, 1); // серый: в процессе обработки
        for (T neighbor : getOutgoing(v)) {
            int c = color.getOrDefault(neighbor, 0);
            if (c == 1) return true;               // нашли обратное ребро -> цикл
            if (c == 0 && hasCycleDfs(neighbor, color)) return true;
        }
        color.put(v, 2); // чёрный: обработан
        return false;
    }


}
