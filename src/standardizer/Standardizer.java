package standardizer;

import parser.ASTNode;

import java.util.ArrayList;
import java.util.List;

public class Standardizer {

    public static ASTNode standardize(ASTNode n) {
        // post-order: standardize children first
        for (int i = 0; i < n.children.size(); i++) {
            n.children.set(i, standardize(n.children.get(i)));
        }
        switch (n.label) {
            case "let": {
                // let(=(X,E), P) -> gamma(lambda(X,P), E)
                ASTNode eq = n.children.get(0);
                ASTNode P = n.children.get(1);
                ASTNode X = eq.children.get(0);
                ASTNode E = eq.children.get(1);
                ASTNode lam = node("lambda", X, P);
                return node("gamma", lam, E);
            }
            case "where": {
                // where(P, =(X,E)) -> gamma(lambda(X,P), E)
                ASTNode P = n.children.get(0);
                ASTNode eq = n.children.get(1);
                ASTNode X = eq.children.get(0);
                ASTNode E = eq.children.get(1);
                ASTNode lam = node("lambda", X, P);
                return node("gamma", lam, E);
            }
            case "fcn_form": {
                // fcn_form(F, V1..Vn, E) -> =(F, lambda(V1, lambda(V2, ... lambda(Vn, E))))
                ASTNode F = n.children.get(0);
                ASTNode E = n.children.get(n.children.size() - 1);
                ASTNode body = E;
                for (int i = n.children.size() - 2; i >= 1; i--) {
                    body = node("lambda", n.children.get(i), body);
                }
                return node("=", F, body);
            }
            case "lambda": {
                // lambda(V1..Vn, E) -> nested lambdas (only if n > 1 vars)
                if (n.children.size() <= 2) return n;
                ASTNode E = n.children.get(n.children.size() - 1);
                ASTNode body = E;
                for (int i = n.children.size() - 2; i >= 0; i--) {
                    body = node("lambda", n.children.get(i), body);
                }
                return body;
            }
            case "within": {
                // within(=(X1,E1), =(X2,E2)) -> =(X2, gamma(lambda(X1,E2), E1))
                ASTNode eq1 = n.children.get(0);
                ASTNode eq2 = n.children.get(1);
                ASTNode X1 = eq1.children.get(0);
                ASTNode E1 = eq1.children.get(1);
                ASTNode X2 = eq2.children.get(0);
                ASTNode E2 = eq2.children.get(1);
                ASTNode lam = node("lambda", X1, E2);
                return node("=", X2, node("gamma", lam, E1));
            }
            case "and": {
                // and(=(X1,E1)..=(Xn,En)) -> =(,(X1..Xn), tau(E1..En))
                List<ASTNode> xs = new ArrayList<>();
                List<ASTNode> es = new ArrayList<>();
                for (ASTNode eq : n.children) {
                    xs.add(eq.children.get(0));
                    es.add(eq.children.get(1));
                }
                return node("=", new ASTNode(",", xs), new ASTNode("tau", es));
            }
            case "rec": {
                // rec(=(X,E)) -> =(X, gamma(Y*, lambda(X,E)))
                ASTNode eq = n.children.get(0);
                ASTNode X = eq.children.get(0);
                ASTNode E = eq.children.get(1);
                ASTNode lam = node("lambda", X, E);
                ASTNode g = node("gamma", new ASTNode("<Y*>"), lam);
                return node("=", X, g);
            }
            case "@": {
                // @(E1, N, E2) -> gamma(gamma(N, E1), E2)
                ASTNode E1 = n.children.get(0);
                ASTNode N = n.children.get(1);
                ASTNode E2 = n.children.get(2);
                return node("gamma", node("gamma", N, E1), E2);
            }
            default:
                return n;
        }
    }

    private static ASTNode node(String label, ASTNode... kids) {
        List<ASTNode> list = new ArrayList<>();
        for (ASTNode k : kids) list.add(k);
        return new ASTNode(label, list);
    }
}
