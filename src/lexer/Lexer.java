package lexer;

import java.util.HashSet;
import java.util.Set;

public class Lexer {
    private final String src;
    private int pos;
    private Token peeked;

    private static final Set<String> KEYWORDS = new HashSet<>();
    static {
        String[] kws = {"let","in","fn","where","aug","or","not","gr","ge","ls","le",
                        "eq","ne","true","false","nil","dummy","within","and","rec"};
        for (String k : kws) KEYWORDS.add(k);
    }

    private static final String OP_SYMS = "+-*<>&.@/:=~|$!#%^_[]{}\"`?";

    public Lexer(String src) {
        this.src = src;
        this.pos = 0;
    }

    private boolean isOpSym(char c) {
        return OP_SYMS.indexOf(c) >= 0;
    }

    private void skipWhitespaceAndComments() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                while (pos < src.length() && src.charAt(pos) != '\n') pos++;
            } else {
                break;
            }
        }
    }

    public Token peek() {
        if (peeked == null) peeked = readInternal();
        return peeked;
    }

    public Token read() {
        if (peeked != null) {
            Token t = peeked;
            peeked = null;
            return t;
        }
        return readInternal();
    }

    private Token readInternal() {
        skipWhitespaceAndComments();
        if (pos >= src.length()) return new Token(TokenType.EOF, "");
        char c = src.charAt(pos);

        if (Character.isLetter(c)) {
            int start = pos;
            while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) pos++;
            String word = src.substring(start, pos);
            if (KEYWORDS.contains(word)) return new Token(TokenType.KEYWORD, word);
            return new Token(TokenType.IDENTIFIER, word);
        }

        if (Character.isDigit(c)) {
            int start = pos;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            return new Token(TokenType.INTEGER, src.substring(start, pos));
        }

        if (c == '\'') {
            int start = pos;
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length() && src.charAt(pos) != '\'') {
                char ch = src.charAt(pos);
                if (ch == '\\' && pos + 1 < src.length()) {
                    sb.append(ch);
                    sb.append(src.charAt(pos + 1));
                    pos += 2;
                } else {
                    sb.append(ch);
                    pos++;
                }
            }
            if (pos < src.length()) pos++; // skip closing '
            return new Token(TokenType.STRING, sb.toString());
        }

        if (c == '(' || c == ')' || c == ';' || c == ',') {
            pos++;
            return new Token(TokenType.PUNCTUATION, String.valueOf(c));
        }

        if (isOpSym(c)) {
            int start = pos;
            while (pos < src.length() && isOpSym(src.charAt(pos))) pos++;
            return new Token(TokenType.OPERATOR, src.substring(start, pos));
        }

        throw new RuntimeException("Lexer: unexpected char '" + c + "' at " + pos);
    }
}
