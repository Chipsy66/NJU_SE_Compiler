import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

public class Main
{
    static int error = 0;

    static int lineNo = 0;

    static int column = 0;

    static String newName = "";

    static String store = "";

    static ArrayList<Pair<Integer,Integer>> hit;

    public static boolean hits(int line, int col)
    {
        for (Pair<Integer,Integer> pair:hit)
        {
            if (pair.a==line&&pair.b==col)
                return true;
        }
        return false;
    }

    static class MyErrorListener extends BaseErrorListener{
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
//            System.err.print("Error type B at Line "+line+":\n");
//            Main.a=1;
        }
    }

    static class Visitor extends SysYParserBaseVisitor<Void>
    {
        String[] keyTable = new String[] {
                "[orange]", "[orange]", "[orange]", "[orange]", "[orange]", "[orange]", "[orange]",
                "[orange]", "[orange]",
                "[blue]", "[blue]", "[blue]", "[blue]", "[blue]", "[blue]", "[blue]", "[blue]", "[blue]", "[blue]",
                "[blue]", "[blue]", "[blue]", "[blue]", "[blue]",
                "", "", "", "",
                "", "", "", "",
                "[red]",
                "[green]",
                "", "", "", "", "", "",
                "", "", "", "", "", ""
        };
        String[] tokenMap =new String[] {
                null, "CONST", "INT", "VOID", "IF", "ELSE", "WHILE", "BREAK", "CONTINUE",
                "RETURN", "PLUS", "MINUS", "MUL", "DIV", "MOD", "ASSIGN", "EQ", "NEQ",
                "LT", "GT", "LE", "GE", "NOT", "AND", "OR", "L_PAREN", "R_PAREN", "L_BRACE",
                "R_BRACE", "L_BRACKT", "R_BRACKT", "COMMA", "SEMICOLON", "IDENT", "INTEGR_CONST",
                "WS", "LINE_COMMENT", "MULTILINE_COMMENT"
        };
        String[] parserMap = new String[] {
                "program", "compUnit", "decl", "constDecl", "bType", "constDef", "constInitVal",
                "varDecl", "varDef", "initVal", "funcDef", "funcType", "funcFParams",
                "funcFParam", "block", "blockItem", "stmt", "exp", "cond", "lVal", "primaryExp",
                "number", "unaryExp", "unaryOp", "funcRParams", "mulExp", "addExp", "relExp",
                "eqExp", "lAndExp", "lOrExp", "constExp"
        };
        @Override
        public Void visitChildren(RuleNode node) {
            if (Main.error ==1)
                return null;

            String ruleName = SysYParserParser.ruleNames[node.getRuleContext().getRuleIndex()];

            int depth = node.getRuleContext().depth();
            depth--;
            for (int i=0;i<depth;i++)
                System.err.print("  ");
            System.err.println(((""+ruleName.charAt(0)).toUpperCase()+ruleName.substring(1)).trim());

            return super.visitChildren(node);
        }

        @Override
        public Void visitTerminal(TerminalNode node) {
            if (Main.error ==1)
                return null;
            if (node.getSymbol().getType()!=-1)
            {
                String type = SysYLexerLexer.ruleNames[node.getSymbol().getType()-1];
                String key = keyTable[node.getSymbol().getType()-1];
                if (key.length()>0)
                {
                    RuleNode parent = (RuleNode) node.getParent();
                    int depth = parent.getRuleContext().depth();
                    for (int i=0;i<depth;i++)
                        System.err.print("  ");
                    String temp = node.getSymbol().getText();
                    if (Objects.equals(type, "INTEGR_CONST"))
                    {
                        if (temp.startsWith("0x")||temp.startsWith("0X"))
                            temp = String.valueOf(Integer.parseInt(temp.substring(2),16));
                        else if (temp.startsWith("0")&&temp.length()>=2)
                            temp = String.valueOf(Integer.parseInt(temp,8));
                    }
                    if (hits(node.getSymbol().getLine(),node.getSymbol().getCharPositionInLine()))
                    {
                        temp = newName;
                    }

                    String toPrint = temp.trim() + " " + type + key;
                    System.err.println(toPrint);
                }
            }
            return super.visitTerminal(node);
        }
    }
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("input path is required");
        }
        String source = args[0];
        store = args[1];
        CharStream input = CharStreams.fromFileName(source);


        SysYLexerLexer sysYLexer = new SysYLexerLexer(input);

        CommonTokenStream tokens = new CommonTokenStream(sysYLexer);
        SysYParserParser sysYParser = new SysYParserParser(tokens);
        sysYParser.removeErrorListeners();


        ParseTree tree = sysYParser.program();

//        ParseTreeWalker parseTreeWalker = new ParseTreeWalker();
//        LLVMListener listener = new LLVMListener();
//        parseTreeWalker.walk(listener, tree);
//
//        if (Main.error ==1)
//            return ;

        LLVMVisitor visitor = new LLVMVisitor();
        visitor.visit(tree);

//        sysYLexer.removeErrorListeners();
//        MyErrorListener myErrorListener = new MyErrorListener();
//        sysYLexer.addErrorListener(myErrorListener);
//        List<? extends Token> allTokens = sysYLexer.getAllTokens();
//        String[] types = new String[] {
//                null, "CONST", "INT", "VOID", "IF", "ELSE", "WHILE", "BREAK", "CONTINUE",
//                "RETURN", "PLUS", "MINUS", "MUL", "DIV", "MOD", "ASSIGN", "EQ", "NEQ",
//                "LT", "GT", "LE", "GE", "NOT", "AND", "OR", "L_PAREN", "R_PAREN", "L_BRACE",
//                "R_BRACE", "L_BRACKT", "R_BRACKT", "COMMA", "SEMICOLON", "IDENT", "INTEGR_CONST",
//                "WS", "LINE_COMMENT", "MULTILINE_COMMENT"
//        };
//        if (a==1)
//            return;
//        for (Token token:allTokens)
//        {
//            String temp = token.getText();
//            if (Objects.equals(types[token.getType()], "INTEGR_CONST"))
//            {
//                if (temp.startsWith("0x")||temp.startsWith("0X"))
//                    temp = String.valueOf(Integer.parseInt(temp.substring(2),16));
//                else if (temp.startsWith("0")&&temp.length()>=2)
//                    temp = String.valueOf(Integer.parseInt(temp,8));
//            }
//            if (Objects.equals(types[token.getType()], "LINE_COMMENT")
//                    ||Objects.equals(types[token.getType()], "MULTILINE_COMMENT"))
//                continue;
//            System.err.print(types[token.getType()]+" "+temp+" at Line "+token.getLine()+".\n");
//        }



    }


}
