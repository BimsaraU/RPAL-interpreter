package parser;

import lexer.Lexer;
import lexer.Token;
import lexer.TokenType;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class Parser {
    private final Lexer lex;
    private final Deque<ASTNode> stack = new ArrayDeque<>();

    public Parser(Lexer lex) { this.lex = lex; }

    private Token peek() { return lex.peek(); }

    private boolean isKw(String s) {
        Token t = peek();
        return t.type == TokenType.KEYWORD && t.value.equals(s);
    }

    private boolean isOp(String s) {
        Token t = peek();
        return t.type == TokenType.OPERATOR && t.value.equals(s);
    }

    private boolean isPunc(String s) {
        Token t = peek();
        return t.type == TokenType.PUNCTUATION && t.value.equals(s);
    }

    private void eatKw(String s) {
        if (!isKw(s)) throw new RuntimeException("Expected keyword '" + s + "', got " + peek());
        lex.read();
    }

    private void eatOp(String s) {
        if (!isOp(s)) throw new RuntimeException("Expected operator '" + s + "', got " + peek());
        lex.read();
    }

    private void eatPunc(String s) {
        if (!isPunc(s)) throw new RuntimeException("Expected punct '" + s + "', got " + peek());
        lex.read();
    }

    private void build(String label, int n) {
        List<ASTNode> kids = new ArrayList<>();
        for (int i = 0; i < n; i++) kids.add(0, stack.pop());
        ASTNode node = new ASTNode(label, kids);
        stack.push(node);
    }

    public ASTNode parse() {
        E();
        if (peek().type != TokenType.EOF) throw new RuntimeException("Trailing tokens: " + peek());
        return stack.pop();
    }

    // E -> 'let' D 'in' E | 'fn' Vb+ '.' E | Ew
    private void E() {
        if (isKw("let")) {
            lex.read();
            D();
            eatKw("in");
            E();
            build("let", 2);
        } else if (isKw("fn")) {
            lex.read();
            int n = 0;
            do { Vb(); n++; } while (peek().type == TokenType.IDENTIFIER || isPunc("("));
            eatOp(".");
            E();
            build("lambda", n + 1);
        } else {
            Ew();
        }
    }

    // Ew -> T 'where' Dr | T
    private void Ew() {
        T();
        if (isKw("where")) {
            lex.read();
            Dr();
            build("where", 2);
        }
    }

    // T -> Ta (',' Ta)+ => tau | Ta
    private void T() {
        Ta();
        int n = 1;
        while (isPunc(",")) {
            lex.read();
            Ta();
            n++;
        }
        if (n > 1) build("tau", n);
    }

    // Ta -> Ta 'aug' Tc | Tc  (left assoc)
    private void Ta() {
        Tc();
        while (isKw("aug")) {
            lex.read();
            Tc();
            build("aug", 2);
        }
    }

    // Tc -> B '->' Tc '|' Tc | B  (right-assoc on then/else)
    private void Tc() {
        B();
        if (isOp("->")) {
            lex.read();
            Tc();
            eatOp("|");
            Tc();
            build("->", 3);
        }
    }

    // B -> B 'or' Bt | Bt
    private void B() {
        Bt();
        while (isKw("or")) {
            lex.read();
            Bt();
            build("or", 2);
        }
    }

    // Bt -> Bt '&' Bs | Bs
    private void Bt() {
        Bs();
        while (isOp("&")) {
            lex.read();
            Bs();
            build("&", 2);
        }
    }

    // Bs -> 'not' Bp | Bp
    private void Bs() {
        if (isKw("not")) {
            lex.read();
            Bp();
            build("not", 1);
        } else {
            Bp();
        }
    }

    // Bp -> A (rel A)? | A
    private void Bp() {
        A();
        String rel = null;
        if (isKw("gr") || isOp(">")) rel = "gr";
        else if (isKw("ge") || isOp(">=")) rel = "ge";
        else if (isKw("ls") || isOp("<")) rel = "ls";
        else if (isKw("le") || isOp("<=")) rel = "le";
        else if (isKw("eq")) rel = "eq";
        else if (isKw("ne")) rel = "ne";
        if (rel != null) {
            lex.read();
            A();
            build(rel, 2);
        }
    }

    // A -> A '+' At | A '-' At | '+' At | '-' At | At
    private void A() {
        if (isOp("+")) {
            lex.read();
            At();
        } else if (isOp("-")) {
            lex.read();
            At();
            build("neg", 1);
        } else {
            At();
        }
        while (isOp("+") || isOp("-")) {
            String op = peek().value;
            lex.read();
            At();
            build(op, 2);
        }
    }

    // At -> At '*' Af | At '/' Af | Af
    private void At() {
        Af();
        while (isOp("*") || isOp("/")) {
            String op = peek().value;
            lex.read();
            Af();
            build(op, 2);
        }
    }

    // Af -> Ap '**' Af | Ap  (right-assoc)
    private void Af() {
        Ap();
        if (isOp("**")) {
            lex.read();
            Af();
            build("**", 2);
        }
    }

    // Ap -> Ap '@' '<ID>' R | R
    private void Ap() {
        R();
        while (isOp("@")) {
            lex.read();
            if (peek().type != TokenType.IDENTIFIER)
                throw new RuntimeException("Expected ID after @, got " + peek());
            String id = lex.read().value;
            stack.push(new ASTNode("<ID:" + id + ">"));
            R();
            build("@", 3);
        }
    }

    // R -> R Rn | Rn  (left-assoc gamma)
    private void R() {
        Rn();
        while (isRnStart()) {
            Rn();
            build("gamma", 2);
        }
    }

    private boolean isRnStart() {
        Token t = peek();
        if (t.type == TokenType.IDENTIFIER) return true;
        if (t.type == TokenType.INTEGER) return true;
        if (t.type == TokenType.STRING) return true;
        if (t.type == TokenType.KEYWORD) {
            return t.value.equals("true") || t.value.equals("false")
                || t.value.equals("nil") || t.value.equals("dummy");
        }
        if (t.type == TokenType.PUNCTUATION && t.value.equals("(")) return true;
        return false;
    }

    // Rn -> <ID> | <INT> | <STR> | true | false | nil | dummy | '(' E ')'
    private void Rn() {
        Token t = peek();
        if (t.type == TokenType.IDENTIFIER) {
            lex.read();
            stack.push(new ASTNode("<ID:" + t.value + ">"));
        } else if (t.type == TokenType.INTEGER) {
            lex.read();
            stack.push(new ASTNode("<INT:" + t.value + ">"));
        } else if (t.type == TokenType.STRING) {
            lex.read();
            stack.push(new ASTNode("<STR:'" + t.value + "'>"));
        } else if (isKw("true") || isKw("false") || isKw("nil") || isKw("dummy")) {
            lex.read();
            stack.push(new ASTNode(t.value));
        } else if (isPunc("(")) {
            lex.read();
            E();
            eatPunc(")");
        } else {
            throw new RuntimeException("Unexpected token in Rn: " + t);
        }
    }

    // D -> Da 'within' D | Da
    private void D() {
        Da();
        if (isKw("within")) {
            lex.read();
            D();
            build("within", 2);
        }
    }

    // Da -> Dr ('and' Dr)+ | Dr
    private void Da() {
        Dr();
        int n = 1;
        while (isKw("and")) {
            lex.read();
            Dr();
            n++;
        }
        if (n > 1) build("and", n);
    }

    // Dr -> 'rec' Db | Db
    private void Dr() {
        if (isKw("rec")) {
            lex.read();
            Db();
            build("rec", 1);
        } else {
            Db();
        }
    }

    // Db -> Vl '=' E | <ID> Vb+ '=' E | '(' D ')'
    private void Db() {
        if (isPunc("(")) {
            lex.read();
            D();
            eatPunc(")");
            return;
        }
        if (peek().type != TokenType.IDENTIFIER)
            throw new RuntimeException("Expected ID in Db, got " + peek());
        // Lookahead: <ID> then (',' or '=') means Vl-based; <ID> then ID/( means fcn_form
        String firstId = lex.read().value;
        stack.push(new ASTNode("<ID:" + firstId + ">"));
        if (isPunc(",")) {
            // Vl path: collect more IDs
            int n = 1;
            while (isPunc(",")) {
                lex.read();
                if (peek().type != TokenType.IDENTIFIER)
                    throw new RuntimeException("Expected ID after , in Vl");
                String idv = lex.read().value;
                stack.push(new ASTNode("<ID:" + idv + ">"));
                n++;
            }
            build(",", n);
            eatOp("=");
            E();
            build("=", 2);
        } else if (isOp("=")) {
            lex.read();
            E();
            build("=", 2);
        } else {
            // fcn_form: ID was function name, then Vb+
            int n = 0;
            while (peek().type == TokenType.IDENTIFIER || isPunc("(")) {
                Vb();
                n++;
            }
            if (n == 0) throw new RuntimeException("Expected Vb in fcn_form");
            eatOp("=");
            E();
            build("fcn_form", n + 2);
        }
    }

    // Vb -> <ID> | '(' Vl ')' | '(' ')'
    private void Vb() {
        if (peek().type == TokenType.IDENTIFIER) {
            String id = lex.read().value;
            stack.push(new ASTNode("<ID:" + id + ">"));
        } else if (isPunc("(")) {
            lex.read();
            if (isPunc(")")) {
                lex.read();
                stack.push(new ASTNode("()"));
            } else {
                if (peek().type != TokenType.IDENTIFIER)
                    throw new RuntimeException("Expected ID in Vl, got " + peek());
                String id = lex.read().value;
                stack.push(new ASTNode("<ID:" + id + ">"));
                int n = 1;
                while (isPunc(",")) {
                    lex.read();
                    if (peek().type != TokenType.IDENTIFIER)
                        throw new RuntimeException("Expected ID after , in Vl");
                    String idv = lex.read().value;
                    stack.push(new ASTNode("<ID:" + idv + ">"));
                    n++;
                }
                eatPunc(")");
                if (n > 1) build(",", n);
            }
        } else {
            throw new RuntimeException("Expected Vb, got " + peek());
        }
    }
}
