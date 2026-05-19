package cse;

import parser.ASTNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CSEMachine {

    // ===== Symbol model =====
    public enum K {
        NAME, INT, STR, BOOL, NIL, DUMMY, YSTAR,
        LAMBDA, ETA, TUPLE, GAMMA, TAU, AUG,
        BETA, DELTA_THEN, DELTA_ELSE,
        OP, ENV_MARKER, BUILTIN
    }

    public static class Symbol {
        public K kind;
        public String name;       // NAME, OP, BUILTIN, STR (raw)
        public long intVal;       // INT
        public boolean boolVal;   // BOOL
        public int deltaIdx;      // LAMBDA, DELTA_THEN, DELTA_ELSE
        public List<String> vars; // LAMBDA bound vars (1 or more if comma)
        public Environment env;   // LAMBDA captured env, ENV_MARKER env
        public List<Symbol> items; // TUPLE
        public int tauN;          // TAU
        public Symbol etaLambda;  // ETA
        public Symbol partial;    // BUILTIN curry partial (e.g. Conc)

        public Symbol(K k) { this.kind = k; }
    }

    public static Symbol mkInt(long v) { Symbol s = new Symbol(K.INT); s.intVal = v; return s; }
    public static Symbol mkStr(String v) { Symbol s = new Symbol(K.STR); s.name = v; return s; }
    public static Symbol mkBool(boolean v) { Symbol s = new Symbol(K.BOOL); s.boolVal = v; return s; }
    public static Symbol mkName(String n) { Symbol s = new Symbol(K.NAME); s.name = n; return s; }
    public static Symbol mkOp(String n) { Symbol s = new Symbol(K.OP); s.name = n; return s; }
    public static Symbol mkBuiltin(String n) { Symbol s = new Symbol(K.BUILTIN); s.name = n; return s; }
    public static Symbol mkNil() { Symbol s = new Symbol(K.NIL); s.items = new ArrayList<>(); return s; }
    public static Symbol mkDummy() { return new Symbol(K.DUMMY); }
    public static Symbol mkYstar() { return new Symbol(K.YSTAR); }
    public static Symbol mkTuple(List<Symbol> items) { Symbol s = new Symbol(K.TUPLE); s.items = items; return s; }

    // ===== Environment =====
    public static class Environment {
        public final int id;
        public final Environment parent;
        public final Map<String, Symbol> bindings = new HashMap<>();
        public Environment(int id, Environment parent) {
            this.id = id; this.parent = parent;
        }
        public Symbol lookup(String name) {
            Environment e = this;
            while (e != null) {
                Symbol v = e.bindings.get(name);
                if (v != null) return v;
                e = e.parent;
            }
            return null;
        }
    }

    // ===== Builtins =====
    private static final Set<String> BUILTINS = new HashSet<>(Arrays.asList(
        "Print", "print", "Conc", "Stem", "Stern", "Order", "Null",
        "Isinteger", "Istruthvalue", "Isstring", "Istuple", "Isfunction", "Isdummy",
        "ItoS"
    ));

    // ===== Machine state =====
    private final List<List<Symbol>> deltas = new ArrayList<>();
    private boolean trace = false;
    private int envCounter = 0;

    public CSEMachine(ASTNode st) {
        flatten(st);
    }

    public void setTrace(boolean t) { this.trace = t; }

    // ===== Flatten ST → deltas =====
    private int newDelta() {
        deltas.add(new ArrayList<>());
        return deltas.size() - 1;
    }

    private void flatten(ASTNode root) {
        int k0 = newDelta();
        flattenInto(root, k0);
    }

    private void flattenInto(ASTNode n, int into) {
        List<Symbol> out = deltas.get(into);
        String lbl = n.label;
        if (lbl.startsWith("<ID:")) {
            String name = lbl.substring(4, lbl.length() - 1);
            if (BUILTINS.contains(name)) out.add(mkBuiltin(name));
            else out.add(mkName(name));
            return;
        }
        if (lbl.startsWith("<INT:")) {
            String v = lbl.substring(5, lbl.length() - 1);
            out.add(mkInt(Long.parseLong(v)));
            return;
        }
        if (lbl.startsWith("<STR:")) {
            // form: <STR:'...'>
            String v = lbl.substring(6, lbl.length() - 2); // strip <STR:' and '>
            out.add(mkStr(v));
            return;
        }
        switch (lbl) {
            case "true": out.add(mkBool(true)); return;
            case "false": out.add(mkBool(false)); return;
            case "nil": out.add(mkNil()); return;
            case "dummy": out.add(mkDummy()); return;
            case "<Y*>": out.add(mkYstar()); return;
            case "lambda": {
                // children: var, body
                ASTNode varNode = n.children.get(0);
                ASTNode body = n.children.get(1);
                int kBody = newDelta();
                flattenInto(body, kBody);
                Symbol s = new Symbol(K.LAMBDA);
                s.deltaIdx = kBody;
                s.vars = collectVars(varNode);
                out.add(s);
                return;
            }
            case "->": {
                ASTNode cond = n.children.get(0);
                ASTNode thenN = n.children.get(1);
                ASTNode elseN = n.children.get(2);
                int kT = newDelta();
                flattenInto(thenN, kT);
                int kE = newDelta();
                flattenInto(elseN, kE);
                Symbol dt = new Symbol(K.DELTA_THEN); dt.deltaIdx = kT;
                Symbol de = new Symbol(K.DELTA_ELSE); de.deltaIdx = kE;
                Symbol b = new Symbol(K.BETA);
                // cond first (executes first), then BETA, then DT, DE so BETA can poll them.
                flattenInto(cond, into);
                out.add(b);
                out.add(dt);
                out.add(de);
                return;
            }
            case "gamma": {
                // Convention: rator on top. Flatten rand first, then rator, then γ.
                flattenInto(n.children.get(1), into);
                flattenInto(n.children.get(0), into);
                out.add(new Symbol(K.GAMMA));
                return;
            }
            case "tau": {
                for (ASTNode c : n.children) flattenInto(c, into);
                Symbol s = new Symbol(K.TAU);
                s.tauN = n.children.size();
                out.add(s);
                return;
            }
            case "aug": {
                for (ASTNode c : n.children) flattenInto(c, into);
                out.add(new Symbol(K.AUG));
                return;
            }
            // operators
            case "+": case "-": case "*": case "/": case "**":
            case "or": case "&": case "gr": case "ge": case "ls": case "le":
            case "eq": case "ne": {
                for (ASTNode c : n.children) flattenInto(c, into);
                out.add(mkOp(lbl));
                return;
            }
            case "neg": case "not": {
                flattenInto(n.children.get(0), into);
                out.add(mkOp(lbl));
                return;
            }
            default:
                throw new RuntimeException("CSE flatten: unhandled label '" + lbl + "'");
        }
    }

    private List<String> collectVars(ASTNode varNode) {
        List<String> vs = new ArrayList<>();
        if (varNode.label.equals(",")) {
            for (ASTNode c : varNode.children) {
                vs.add(stripId(c.label));
            }
        } else if (varNode.label.equals("()")) {
            vs.add("");
        } else {
            vs.add(stripId(varNode.label));
        }
        return vs;
    }

    private String stripId(String lbl) {
        if (lbl.startsWith("<ID:")) return lbl.substring(4, lbl.length() - 1);
        return lbl;
    }

    // ===== Print control structures (debug) =====
    public void printDeltas() {
        for (int i = 0; i < deltas.size(); i++) {
            StringBuilder sb = new StringBuilder("δ").append(i).append(": [");
            List<Symbol> d = deltas.get(i);
            for (int j = 0; j < d.size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(symStr(d.get(j)));
            }
            sb.append("]");
            System.out.println(sb);
        }
    }

    // ===== Run =====
    // Convention: control C and stack S are deques.
    //   C: top = front (we pollFirst / addFirst).
    //   S: top = front (we push to front / pop from front).
    private LinkedList<Symbol> C;
    private LinkedList<Symbol> S;
    private Environment curEnv;
    private LinkedList<Environment> envStack;

    public void run() {
        C = new LinkedList<>();
        S = new LinkedList<>();
        envStack = new LinkedList<>();
        Environment e0 = new Environment(envCounter++, null);
        curEnv = e0;
        envStack.push(e0);

        // initial: env marker e0 then δ0 contents
        Symbol em = new Symbol(K.ENV_MARKER); em.env = e0;
        C.addLast(em);
        // Append δ0 contents in order — but top=front. We want δ0[0] processed first, then δ0[1], ...
        // With top=front, processing order = front to back. So just addAll preserving order, but env marker is at index 0 — we want env marker processed FIRST? No, env marker stays at bottom and is hit only when nothing left from δ0.
        // Move env marker to BACK; control = δ0 contents then env marker.
        C.clear();
        C.addAll(deltas.get(0));
        C.addLast(em);
        // Also push env marker onto S (rule for env switching matches by popping marker from S below result).
        S.addFirst(em);

        int step = 0;
        while (!C.isEmpty()) {
            step++;
            if (trace) traceStep(step);
            Symbol top = C.pollFirst();
            apply(top);
        }
    }

    private void apply(Symbol t) {
        switch (t.kind) {
            case INT: case STR: case BOOL: case NIL: case DUMMY: case YSTAR:
            case TUPLE:
                S.addFirst(t);
                break;
            case NAME: {
                Symbol v = curEnv.lookup(t.name);
                if (v == null) throw new RuntimeException("Unbound name: " + t.name);
                S.addFirst(v);
                break;
            }
            case BUILTIN:
                S.addFirst(t);
                break;
            case LAMBDA: {
                // capture current env
                Symbol nl = new Symbol(K.LAMBDA);
                nl.deltaIdx = t.deltaIdx;
                nl.vars = t.vars;
                nl.env = curEnv;
                S.addFirst(nl);
                break;
            }
            case GAMMA: {
                // rator on top
                Symbol rator = S.pollFirst();
                Symbol rand = S.pollFirst();
                applyGamma(rator, rand);
                break;
            }
            case TAU: {
                List<Symbol> items = new ArrayList<>();
                // pushed in source order; top = last child. Pop and insert at front.
                for (int i = 0; i < t.tauN; i++) items.add(0, S.pollFirst());
                S.addFirst(mkTuple(items));
                break;
            }
            case AUG: {
                // flatten aug(tup, val): tup pushed first, val on top.
                Symbol val = S.pollFirst();
                Symbol tup = S.pollFirst();
                List<Symbol> items = new ArrayList<>();
                if (tup.kind == K.NIL) {
                    // nil aug v => (v)
                } else if (tup.kind == K.TUPLE) {
                    items.addAll(tup.items);
                } else {
                    throw new RuntimeException("aug: lhs not tuple/nil: " + tup.kind);
                }
                items.add(val);
                S.addFirst(mkTuple(items));
                break;
            }
            case OP:
                applyOp(t.name);
                break;
            case BETA: {
                Symbol b = S.pollFirst();
                Symbol dt = C.pollFirst();
                Symbol de = C.pollFirst();
                if (dt.kind != K.DELTA_THEN || de.kind != K.DELTA_ELSE)
                    throw new RuntimeException("BETA: expected then/else deltas");
                int idx = b.boolVal ? dt.deltaIdx : de.deltaIdx;
                // prepend chosen delta in order
                List<Symbol> body = deltas.get(idx);
                for (int i = body.size() - 1; i >= 0; i--) C.addFirst(body.get(i));
                break;
            }
            case DELTA_THEN: case DELTA_ELSE:
                // shouldn't be processed directly — beta consumes them
                throw new RuntimeException("Stray delta_then/else in control");
            case ENV_MARKER: {
                // pop matching env marker from S below result
                Symbol result = S.pollFirst();
                Symbol em = S.pollFirst();
                if (em.kind != K.ENV_MARKER)
                    throw new RuntimeException("Env marker mismatch on S: got " + em.kind);
                S.addFirst(result);
                // restore env
                envStack.pop(); // pop current
                if (!envStack.isEmpty()) curEnv = envStack.peek();
                break;
            }
            case ETA: {
                S.addFirst(t);
                break;
            }
            default:
                throw new RuntimeException("apply: unhandled " + t.kind);
        }
    }

    private void applyGamma(Symbol rator, Symbol rand) {
        if (rator.kind == K.LAMBDA) {
            Environment newEnv = new Environment(envCounter++, rator.env);
            // bind vars
            if (rator.vars.size() == 1) {
                String v = rator.vars.get(0);
                if (!v.isEmpty()) newEnv.bindings.put(v, rand);
            } else {
                if (rand.kind != K.TUPLE)
                    throw new RuntimeException("multi-arg lambda: rand not tuple");
                if (rand.items.size() != rator.vars.size())
                    throw new RuntimeException("arity mismatch");
                for (int i = 0; i < rator.vars.size(); i++) {
                    newEnv.bindings.put(rator.vars.get(i), rand.items.get(i));
                }
            }
            envStack.push(newEnv);
            curEnv = newEnv;
            Symbol em = new Symbol(K.ENV_MARKER); em.env = newEnv;
            // push env marker into C (as boundary at bottom of new body's symbols)
            C.addFirst(em);
            List<Symbol> body = deltas.get(rator.deltaIdx);
            for (int i = body.size() - 1; i >= 0; i--) C.addFirst(body.get(i));
            S.addFirst(em);
        } else if (rator.kind == K.YSTAR) {
            // Y* applied to lambda -> Eta(lambda)
            if (rand.kind != K.LAMBDA)
                throw new RuntimeException("Y*: rand not lambda");
            Symbol eta = new Symbol(K.ETA);
            eta.etaLambda = rand;
            S.addFirst(eta);
        } else if (rator.kind == K.ETA) {
            // Y F arg => F (Y F) arg. With rator-on-top convention:
            // After γ1, F(eta) closure on top. γ2 needs arg below it (as rand).
            // Push order: arg deep, eta, then F on top.
            S.addFirst(rand);            // arg
            S.addFirst(rator);           // eta(F) (rand for γ1)
            S.addFirst(rator.etaLambda); // F (rator for γ1, on top)
            C.addFirst(new Symbol(K.GAMMA));
            C.addFirst(new Symbol(K.GAMMA));
        } else if (rator.kind == K.TUPLE) {
            if (rand.kind != K.INT) throw new RuntimeException("tuple index not int");
            int i = (int) rand.intVal;
            if (i < 1 || i > rator.items.size())
                throw new RuntimeException("tuple index out of range: " + i);
            S.addFirst(rator.items.get(i - 1));
        } else if (rator.kind == K.NIL) {
            throw new RuntimeException("apply nil");
        } else if (rator.kind == K.BUILTIN) {
            applyBuiltin(rator, rand);
        } else {
            throw new RuntimeException("gamma: bad rator kind " + rator.kind);
        }
    }

    private void applyBuiltin(Symbol b, Symbol rand) {
        String name = b.name;
        switch (name) {
            case "Print":
            case "print": {
                printValue(rand);
                S.addFirst(mkDummy());
                return;
            }
            case "Order": {
                if (rand.kind == K.NIL) { S.addFirst(mkInt(0)); return; }
                if (rand.kind != K.TUPLE) throw new RuntimeException("Order: not tuple");
                S.addFirst(mkInt(rand.items.size()));
                return;
            }
            case "Null": {
                S.addFirst(mkBool(rand.kind == K.NIL || (rand.kind == K.TUPLE && rand.items.isEmpty())));
                return;
            }
            case "Isinteger": S.addFirst(mkBool(rand.kind == K.INT)); return;
            case "Istruthvalue": S.addFirst(mkBool(rand.kind == K.BOOL)); return;
            case "Isstring": S.addFirst(mkBool(rand.kind == K.STR)); return;
            case "Istuple": S.addFirst(mkBool(rand.kind == K.TUPLE || rand.kind == K.NIL)); return;
            case "Isfunction": S.addFirst(mkBool(rand.kind == K.LAMBDA || rand.kind == K.BUILTIN || rand.kind == K.ETA)); return;
            case "Isdummy": S.addFirst(mkBool(rand.kind == K.DUMMY)); return;
            case "Stem": {
                if (rand.kind != K.STR) throw new RuntimeException("Stem: not str");
                String s = rand.name;
                S.addFirst(mkStr(s.isEmpty() ? "" : s.substring(0, 1)));
                return;
            }
            case "Stern": {
                if (rand.kind != K.STR) throw new RuntimeException("Stern: not str");
                String s = rand.name;
                S.addFirst(mkStr(s.isEmpty() ? "" : s.substring(1)));
                return;
            }
            case "ItoS": {
                if (rand.kind != K.INT) throw new RuntimeException("ItoS: not int");
                S.addFirst(mkStr(Long.toString(rand.intVal)));
                return;
            }
            case "Conc": {
                if (b.partial == null) {
                    // first gamma: capture first string in partial closure
                    Symbol p = new Symbol(K.BUILTIN);
                    p.name = "Conc";
                    p.partial = rand;
                    S.addFirst(p);
                } else {
                    if (b.partial.kind != K.STR || rand.kind != K.STR)
                        throw new RuntimeException("Conc: args not strings");
                    S.addFirst(mkStr(b.partial.name + rand.name));
                }
                return;
            }
            default:
                throw new RuntimeException("Unknown builtin: " + name);
        }
    }

    private void applyOp(String op) {
        switch (op) {
            case "neg": {
                Symbol a = S.pollFirst();
                S.addFirst(mkInt(-a.intVal));
                return;
            }
            case "not": {
                Symbol a = S.pollFirst();
                S.addFirst(mkBool(!a.boolVal));
                return;
            }
        }
        // flatten l, r, op → r on top
        Symbol r = S.pollFirst();
        Symbol l = S.pollFirst();
        switch (op) {
            case "+": S.addFirst(mkInt(l.intVal + r.intVal)); break;
            case "-": S.addFirst(mkInt(l.intVal - r.intVal)); break;
            case "*": S.addFirst(mkInt(l.intVal * r.intVal)); break;
            case "/": S.addFirst(mkInt(l.intVal / r.intVal)); break;
            case "**": S.addFirst(mkInt((long) Math.pow(l.intVal, r.intVal))); break;
            case "gr": S.addFirst(mkBool(l.intVal > r.intVal)); break;
            case "ge": S.addFirst(mkBool(l.intVal >= r.intVal)); break;
            case "ls": S.addFirst(mkBool(l.intVal < r.intVal)); break;
            case "le": S.addFirst(mkBool(l.intVal <= r.intVal)); break;
            case "eq": S.addFirst(mkBool(eqSym(l, r))); break;
            case "ne": S.addFirst(mkBool(!eqSym(l, r))); break;
            case "or": S.addFirst(mkBool(l.boolVal || r.boolVal)); break;
            case "&": S.addFirst(mkBool(l.boolVal && r.boolVal)); break;
            default: throw new RuntimeException("Unknown op: " + op);
        }
    }

    private boolean eqSym(Symbol a, Symbol b) {
        if (a.kind != b.kind) return false;
        switch (a.kind) {
            case INT: return a.intVal == b.intVal;
            case STR: return a.name.equals(b.name);
            case BOOL: return a.boolVal == b.boolVal;
            case NIL: return true;
            case DUMMY: return true;
            default: return false;
        }
    }

    // ===== Print value (match rpal.exe) =====
    private void printValue(Symbol v) {
        System.out.print(valueStr(v, true));
    }

    public String valueStr(Symbol v, boolean unescape) {
        switch (v.kind) {
            case INT: return Long.toString(v.intVal);
            case STR: return unescape ? unescape(v.name) : v.name;
            case BOOL: return v.boolVal ? "true" : "false";
            case NIL: return "nil";
            case DUMMY: return "dummy";
            case TUPLE: {
                if (v.items.isEmpty()) return "nil";
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < v.items.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(valueStr(v.items.get(i), unescape));
                }
                sb.append(")");
                return sb.toString();
            }
            case LAMBDA: {
                String vs = v.vars == null || v.vars.isEmpty() ? "" : String.join(",", v.vars);
                return "[lambda closure: " + vs + ": " + v.deltaIdx + "]";
            }
            case BUILTIN: return "[builtin: " + v.name + "]";
            case ETA: return "[eta closure]";
            case YSTAR: return "[Y*]";
            default: return "<" + v.kind + ">";
        }
    }

    private String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case '\\': sb.append('\\'); break;
                    case '\'': sb.append('\''); break;
                    default: sb.append(c).append(n);
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ===== trace =====
    private void traceStep(int step) {
        System.out.println(); // ensure fresh line after any inline Print output
        StringBuilder sc = new StringBuilder();
        for (Symbol s : C) { sc.append(symStr(s)).append(" "); }
        StringBuilder ss = new StringBuilder();
        for (Symbol s : S) { ss.append(symStr(s)).append(" "); }
        System.out.println("[" + step + "] C: " + sc.toString().trim() + "  | S: " + ss.toString().trim() + "  | env: e" + curEnv.id);
    }

    private String symStr(Symbol s) {
        switch (s.kind) {
            case INT: return Long.toString(s.intVal);
            case STR: return "'" + s.name + "'";
            case BOOL: return s.boolVal ? "true" : "false";
            case NAME: return s.name;
            case OP: return s.name;
            case BUILTIN: return s.name;
            case NIL: return "nil";
            case DUMMY: return "dummy";
            case YSTAR: return "Y*";
            case LAMBDA: return "λ<" + s.deltaIdx + "," + String.join(",", s.vars) + ">";
            case ETA: return "η<" + s.etaLambda.deltaIdx + ">";
            case TUPLE: return valueStr(s, false);
            case GAMMA: return "γ";
            case TAU: return "τ" + s.tauN;
            case AUG: return "aug";
            case BETA: return "β";
            case DELTA_THEN: return "δT" + s.deltaIdx;
            case DELTA_ELSE: return "δE" + s.deltaIdx;
            case ENV_MARKER: return "e" + s.env.id;
        }
        return "?";
    }
}
