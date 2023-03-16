// Generated from ./src/SysYLexer.g4 by ANTLR 4.9.1
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link SysYLexerParser}.
 */
public interface SysYLexerListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link SysYLexerParser#prog}.
	 * @param ctx the parse tree
	 */
	void enterProg(SysYLexerParser.ProgContext ctx);
	/**
	 * Exit a parse tree produced by {@link SysYLexerParser#prog}.
	 * @param ctx the parse tree
	 */
	void exitProg(SysYLexerParser.ProgContext ctx);
}