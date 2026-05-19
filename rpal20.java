import java.nio.file.Files;
import java.nio.file.Path;

import lexer.Lexer;
import parser.Parser;
import parser.ASTNode;
import standardizer.Standardizer;
import cse.CSEMachine;
import util.TreePrinter;

public class rpal20 {
    public static void main(String[] args) throws Exception {
        boolean showAst = false, showSast = false, showCse = false, echoSrc = false;
        String file = null;
        for (String a : args) {
            switch (a) {
                case "-ast": showAst = true; break;
                case "-sast":
                case "-st": showSast = true; break;
                case "-cse": showCse = true; break;
                case "-l": echoSrc = true; break;
                default:
                    if (a.startsWith("-")) {
                        System.err.println("Unknown switch: " + a);
                        System.exit(1);
                    }
                    file = a;
            }
        }
        if (file == null) {
            System.err.println("Usage: java rpal20 [-l] [-ast] [-sast|-st] [-cse] file");
            System.exit(1);
        }

        String src = new String(Files.readAllBytes(Path.of(file)));
        if (echoSrc) System.out.println(src);

        Lexer lex = new Lexer(src);
        Parser p = new Parser(lex);
        ASTNode ast = p.parse();
        if (showAst) {
            TreePrinter.print(ast, 0);
            return;
        }
        ASTNode st = Standardizer.standardize(ast);
        if (showSast) {
            TreePrinter.print(st, 0);
            return;
        }
        CSEMachine m = new CSEMachine(st);
        if (showCse) {
            m.printDeltas();
            m.setTrace(true);
        }
        m.run();
    }
}
