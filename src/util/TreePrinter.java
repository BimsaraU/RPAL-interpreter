package util;

import parser.ASTNode;

public class TreePrinter {
    public static void print(ASTNode n, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append('.');
        sb.append(n.label);
        System.out.println(sb);
        for (ASTNode c : n.children) print(c, depth + 1);
    }
}
