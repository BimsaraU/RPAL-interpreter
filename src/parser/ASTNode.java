package parser;

import java.util.ArrayList;
import java.util.List;

public class ASTNode {
    public String label;
    public List<ASTNode> children = new ArrayList<>();

    public ASTNode(String label) { this.label = label; }

    public ASTNode(String label, List<ASTNode> children) {
        this.label = label;
        this.children = children;
    }

    public static ASTNode leaf(String label) { return new ASTNode(label); }
}
