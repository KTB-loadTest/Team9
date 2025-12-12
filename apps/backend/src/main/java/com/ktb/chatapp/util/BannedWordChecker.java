package com.ktb.chatapp.util;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.Assert;

public class BannedWordChecker {

    private final Node root;

    public BannedWordChecker(Set<String> bannedWords) {
        Assert.notEmpty(bannedWords, "Banned words set must not be empty");
        this.root = new Node();

        int inserted = 0;
        for (String word : bannedWords) {
            if (word == null) {
                continue;
            }
            String normalized = word.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            insert(normalized);
            inserted++;
        }

        if (inserted == 0) {
            throw new IllegalArgumentException("Banned words set must contain at least one non-empty word");
        }

        buildFailureLinks();
    }

    /**
     * 메시지에 금칙어가 하나라도 포함되어 있는지 검사.
     * 시간 복잡도: O(message.length()).
     */
    public boolean containsBannedWord(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String text = message.toLowerCase(Locale.ROOT);
        Node node = root;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            // 현재 노드에서 매칭되는 자식이 나올 때까지 실패 링크를 타고 올라감
            while (node != root && !node.children.containsKey(ch)) {
                node = node.failure;
            }

            node = node.children.getOrDefault(ch, root);

            // 이 노드가, 혹은 실패 링크를 타고 올라간 노드 중 하나가
            // 금칙어의 끝(terminal)이면 true
            if (node.terminal) {
                return true;
            }
        }

        return false;
    }

    // ----- 내부 구현부 -----

    private void insert(String word) {
        Node node = root;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            node = node.children.computeIfAbsent(ch, c -> new Node());
        }
        node.terminal = true;
    }

    private void buildFailureLinks() {
        Queue<Node> queue = new ArrayDeque<>();

        // root의 직계 자식들의 failure는 모두 root
        for (Node child : root.children.values()) {
            child.failure = root;
            queue.add(child);
        }

        // BFS로 failure 링크 구성
        while (!queue.isEmpty()) {
            Node current = queue.poll();

            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char ch = entry.getKey();
                Node child = entry.getValue();

                Node failure = current.failure;
                while (failure != null && !failure.children.containsKey(ch)) {
                    failure = failure.failure;
                }

                if (failure == null) {
                    child.failure = root;
                } else {
                    child.failure = failure.children.get(ch);
                    // 실패 링크를 따라가다 만난 노드가 terminal이면
                    // 현재 노드도 terminal 취급 (금칙어 suffix 매칭)
                    if (child.failure.terminal) {
                        child.terminal = true;
                    }
                }

                queue.add(child);
            }
        }
    }

    private static final class Node {
        private final Map<Character, Node> children = new HashMap<>();
        private Node failure;
        private boolean terminal;
    }
}
