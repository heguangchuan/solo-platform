package com.solo.common.utils;


import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class TreeUtils {


    public static <T, E> List<E> build(List<E> list
            , Function<E, T> parentIdGetter
            , Function<E, T> idGetter
            , Predicate<E> rootCheck
            , BiConsumer<E, List<E>> setSubChildren) {

        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }

        List<E> result = new ArrayList<>();

        Map<T, List<E>> parentMap = new HashMap<>();

        for (E e : list) {
            T parentId = parentIdGetter.apply(e);
            parentMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(e);
        }

        for (E e : list) {
            T id = idGetter.apply(e);
            List<E> children = parentMap.get(id);
            if (children == null) {
                children = new ArrayList<>();
            }
            setSubChildren.accept(e, children);
            if (rootCheck.test(e)) {
                result.add(e);
            }
        }

        return result;
    }


    public static <T, E> List<E> buildTree(
            List<E> list,
            Function<E, T> parentIdGetter,
            Function<E, T> idGetter,
            BiConsumer<E, List<E>> setChildren) {

        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        Set<T> idSet = new HashSet<>(list.size());
        Map<T, List<E>> parentMap = new HashMap<>();

        for (E e : list) {
            T id = idGetter.apply(e);
            idSet.add(id);
            T parentId = parentIdGetter.apply(e);
            parentMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(e);
        }

        List<E> roots = new ArrayList<>();
        for (E e : list) {
            T id = idGetter.apply(e);
            List<E> children = parentMap.get(id);
            if (children == null) {
                children = Collections.emptyList();
            }
            setChildren.accept(e, children);

            T parentId = parentIdGetter.apply(e);
            if (!idSet.contains(parentId)) {
                roots.add(e);
            }
        }

        return roots;
    }

    public static <E> List<E> flatten(List<E> list, Function<E, List<E>> childrenGetter) {

        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        List<E> result = new ArrayList<>();
        Deque<E> stack = new ArrayDeque<>(list.size());
        // 逆序压入根节点，保证遍历顺序从左到右
        for (int i = list.size() - 1; i >= 0; i--) {
            stack.push(list.get(i));
        }
        while (!stack.isEmpty()) {
            E current = stack.pop();
            result.add(current);
            List<E> children = childrenGetter.apply(current);
            if (children != null && !children.isEmpty()) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
        return result;
    }


    public static <E, T> List<E> sonList(
            E root,
            Function<E, T> idGetter,
            Function<E, T> parentIdGetter,
            List<E> allNodes) {

        if (root == null || allNodes == null || allNodes.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建 parentId -> children 映射（O(n)）
        Map<T, List<E>> parentMap = new HashMap<>();
        for (E node : allNodes) {
            T parentId = parentIdGetter.apply(node);
            parentMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
        }

        // 从 root 开始收集所有后代
        List<E> result = new ArrayList<>();
        Deque<E> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            E current = stack.pop();
            T id = idGetter.apply(current);
            List<E> children = parentMap.get(id);
            if (children != null) {
                result.addAll(children);
                stack.addAll(children);
            }
        }

        return result;
    }


    public static <E, T, R> List<R> absolute(
            E e,
            Function<E, T> idGetter,
            Function<E, T> parentIdGetter,
            Function<E, R> resultGetter,
            List<E> list,
            Predicate<E> rootCheck) {

        if (e == null || list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        Map<T, E> entityMap = new HashMap<>();
        for (E item : list) {
            T id = idGetter.apply(item);
            entityMap.put(id, item);
        }

        List<R> path = new ArrayList<>();
        E current = e;
        while (current != null) {
            path.add(resultGetter.apply(current));

            if (rootCheck.test(current)) {
                break;
            }

            T parentId = parentIdGetter.apply(current);
            if (parentId == null) {
                // 防止无限循环：若 parentId 为 null 但 rootCheck 未识别，也终止
                break;
            }

            current = entityMap.get(parentId);
        }

        // 反转：从根 → 当前节点
        Collections.reverse(path);
        return path;
    }

}

