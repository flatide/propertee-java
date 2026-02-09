// Generated from ProperTee.g4 by ANTLR 4.9.3
package com.propertee.parser;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class ProperTeeLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.9.3", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, K_IF=23, K_THEN=24, 
		K_ELSE=25, K_END=26, K_LOOP=27, K_IN=28, K_DO=29, K_BREAK=30, K_CONTINUE=31, 
		K_FUNCTION=32, K_SPAWN=33, K_RETURN=34, K_NOT=35, K_AND=36, K_OR=37, K_TRUE=38, 
		K_FALSE=39, K_INFINITE=40, K_MULTI=41, K_MONITOR=42, GLOBAL_PREFIX=43, 
		ID=44, INTEGER=45, STRING=46, COMMENT=47, BLOCK_COMMENT=48, WS=49;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
			"T__17", "T__18", "T__19", "T__20", "T__21", "K_IF", "K_THEN", "K_ELSE", 
			"K_END", "K_LOOP", "K_IN", "K_DO", "K_BREAK", "K_CONTINUE", "K_FUNCTION", 
			"K_SPAWN", "K_RETURN", "K_NOT", "K_AND", "K_OR", "K_TRUE", "K_FALSE", 
			"K_INFINITE", "K_MULTI", "K_MONITOR", "GLOBAL_PREFIX", "ID", "INTEGER", 
			"STRING", "COMMENT", "BLOCK_COMMENT", "WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'='", "'.'", "'('", "')'", "','", "':'", "'-'", "'*'", "'/'", 
			"'%'", "'+'", "'$'", "'{'", "'}'", "'['", "']'", "'>'", "'<'", "'=='", 
			"'>='", "'<='", "'!='", "'if'", "'then'", "'else'", "'end'", "'loop'", 
			"'in'", "'do'", "'break'", "'continue'", "'function'", "'thread'", "'return'", 
			"'not'", "'and'", "'or'", "'true'", "'false'", "'infinite'", "'multi'", 
			"'monitor'", "'::'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, "K_IF", 
			"K_THEN", "K_ELSE", "K_END", "K_LOOP", "K_IN", "K_DO", "K_BREAK", "K_CONTINUE", 
			"K_FUNCTION", "K_SPAWN", "K_RETURN", "K_NOT", "K_AND", "K_OR", "K_TRUE", 
			"K_FALSE", "K_INFINITE", "K_MULTI", "K_MONITOR", "GLOBAL_PREFIX", "ID", 
			"INTEGER", "STRING", "COMMENT", "BLOCK_COMMENT", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public ProperTeeLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "ProperTee.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\63\u013e\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31"+
		"\t\31\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t"+
		" \4!\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t"+
		"+\4,\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\3\2\3\2\3\3\3"+
		"\3\3\4\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13\3"+
		"\f\3\f\3\r\3\r\3\16\3\16\3\17\3\17\3\20\3\20\3\21\3\21\3\22\3\22\3\23"+
		"\3\23\3\24\3\24\3\24\3\25\3\25\3\25\3\26\3\26\3\26\3\27\3\27\3\27\3\30"+
		"\3\30\3\30\3\31\3\31\3\31\3\31\3\31\3\32\3\32\3\32\3\32\3\32\3\33\3\33"+
		"\3\33\3\33\3\34\3\34\3\34\3\34\3\34\3\35\3\35\3\35\3\36\3\36\3\36\3\37"+
		"\3\37\3\37\3\37\3\37\3\37\3 \3 \3 \3 \3 \3 \3 \3 \3 \3!\3!\3!\3!\3!\3"+
		"!\3!\3!\3!\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3#\3#\3#\3#\3#\3#\3#\3$\3$\3$\3"+
		"$\3%\3%\3%\3%\3&\3&\3&\3\'\3\'\3\'\3\'\3\'\3(\3(\3(\3(\3(\3(\3)\3)\3)"+
		"\3)\3)\3)\3)\3)\3)\3*\3*\3*\3*\3*\3*\3+\3+\3+\3+\3+\3+\3+\3+\3,\3,\3,"+
		"\3-\3-\7-\u010a\n-\f-\16-\u010d\13-\3.\6.\u0110\n.\r.\16.\u0111\3/\3/"+
		"\3/\3/\7/\u0118\n/\f/\16/\u011b\13/\3/\3/\3\60\3\60\3\60\3\60\7\60\u0123"+
		"\n\60\f\60\16\60\u0126\13\60\3\60\3\60\3\61\3\61\3\61\3\61\7\61\u012e"+
		"\n\61\f\61\16\61\u0131\13\61\3\61\3\61\3\61\3\61\3\61\3\62\6\62\u0139"+
		"\n\62\r\62\16\62\u013a\3\62\3\62\3\u012f\2\63\3\3\5\4\7\5\t\6\13\7\r\b"+
		"\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22#\23%\24\'\25)\26"+
		"+\27-\30/\31\61\32\63\33\65\34\67\359\36;\37= ?!A\"C#E$G%I&K\'M(O)Q*S"+
		"+U,W-Y.[/]\60_\61a\62c\63\3\2\b\5\2C\\aac|\6\2\62;C\\aac|\3\2\62;\4\2"+
		"$$^^\4\2\f\f\17\17\6\2\13\f\17\17\"\"==\2\u0144\2\3\3\2\2\2\2\5\3\2\2"+
		"\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21"+
		"\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2"+
		"\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2\2\2\2%\3\2\2\2\2\'\3"+
		"\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2\2\2\61\3\2\2\2\2\63\3"+
		"\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2;\3\2\2\2\2=\3\2\2\2\2?\3"+
		"\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2E\3\2\2\2\2G\3\2\2\2\2I\3\2\2\2\2K\3\2\2"+
		"\2\2M\3\2\2\2\2O\3\2\2\2\2Q\3\2\2\2\2S\3\2\2\2\2U\3\2\2\2\2W\3\2\2\2\2"+
		"Y\3\2\2\2\2[\3\2\2\2\2]\3\2\2\2\2_\3\2\2\2\2a\3\2\2\2\2c\3\2\2\2\3e\3"+
		"\2\2\2\5g\3\2\2\2\7i\3\2\2\2\tk\3\2\2\2\13m\3\2\2\2\ro\3\2\2\2\17q\3\2"+
		"\2\2\21s\3\2\2\2\23u\3\2\2\2\25w\3\2\2\2\27y\3\2\2\2\31{\3\2\2\2\33}\3"+
		"\2\2\2\35\177\3\2\2\2\37\u0081\3\2\2\2!\u0083\3\2\2\2#\u0085\3\2\2\2%"+
		"\u0087\3\2\2\2\'\u0089\3\2\2\2)\u008c\3\2\2\2+\u008f\3\2\2\2-\u0092\3"+
		"\2\2\2/\u0095\3\2\2\2\61\u0098\3\2\2\2\63\u009d\3\2\2\2\65\u00a2\3\2\2"+
		"\2\67\u00a6\3\2\2\29\u00ab\3\2\2\2;\u00ae\3\2\2\2=\u00b1\3\2\2\2?\u00b7"+
		"\3\2\2\2A\u00c0\3\2\2\2C\u00c9\3\2\2\2E\u00d0\3\2\2\2G\u00d7\3\2\2\2I"+
		"\u00db\3\2\2\2K\u00df\3\2\2\2M\u00e2\3\2\2\2O\u00e7\3\2\2\2Q\u00ed\3\2"+
		"\2\2S\u00f6\3\2\2\2U\u00fc\3\2\2\2W\u0104\3\2\2\2Y\u0107\3\2\2\2[\u010f"+
		"\3\2\2\2]\u0113\3\2\2\2_\u011e\3\2\2\2a\u0129\3\2\2\2c\u0138\3\2\2\2e"+
		"f\7?\2\2f\4\3\2\2\2gh\7\60\2\2h\6\3\2\2\2ij\7*\2\2j\b\3\2\2\2kl\7+\2\2"+
		"l\n\3\2\2\2mn\7.\2\2n\f\3\2\2\2op\7<\2\2p\16\3\2\2\2qr\7/\2\2r\20\3\2"+
		"\2\2st\7,\2\2t\22\3\2\2\2uv\7\61\2\2v\24\3\2\2\2wx\7\'\2\2x\26\3\2\2\2"+
		"yz\7-\2\2z\30\3\2\2\2{|\7&\2\2|\32\3\2\2\2}~\7}\2\2~\34\3\2\2\2\177\u0080"+
		"\7\177\2\2\u0080\36\3\2\2\2\u0081\u0082\7]\2\2\u0082 \3\2\2\2\u0083\u0084"+
		"\7_\2\2\u0084\"\3\2\2\2\u0085\u0086\7@\2\2\u0086$\3\2\2\2\u0087\u0088"+
		"\7>\2\2\u0088&\3\2\2\2\u0089\u008a\7?\2\2\u008a\u008b\7?\2\2\u008b(\3"+
		"\2\2\2\u008c\u008d\7@\2\2\u008d\u008e\7?\2\2\u008e*\3\2\2\2\u008f\u0090"+
		"\7>\2\2\u0090\u0091\7?\2\2\u0091,\3\2\2\2\u0092\u0093\7#\2\2\u0093\u0094"+
		"\7?\2\2\u0094.\3\2\2\2\u0095\u0096\7k\2\2\u0096\u0097\7h\2\2\u0097\60"+
		"\3\2\2\2\u0098\u0099\7v\2\2\u0099\u009a\7j\2\2\u009a\u009b\7g\2\2\u009b"+
		"\u009c\7p\2\2\u009c\62\3\2\2\2\u009d\u009e\7g\2\2\u009e\u009f\7n\2\2\u009f"+
		"\u00a0\7u\2\2\u00a0\u00a1\7g\2\2\u00a1\64\3\2\2\2\u00a2\u00a3\7g\2\2\u00a3"+
		"\u00a4\7p\2\2\u00a4\u00a5\7f\2\2\u00a5\66\3\2\2\2\u00a6\u00a7\7n\2\2\u00a7"+
		"\u00a8\7q\2\2\u00a8\u00a9\7q\2\2\u00a9\u00aa\7r\2\2\u00aa8\3\2\2\2\u00ab"+
		"\u00ac\7k\2\2\u00ac\u00ad\7p\2\2\u00ad:\3\2\2\2\u00ae\u00af\7f\2\2\u00af"+
		"\u00b0\7q\2\2\u00b0<\3\2\2\2\u00b1\u00b2\7d\2\2\u00b2\u00b3\7t\2\2\u00b3"+
		"\u00b4\7g\2\2\u00b4\u00b5\7c\2\2\u00b5\u00b6\7m\2\2\u00b6>\3\2\2\2\u00b7"+
		"\u00b8\7e\2\2\u00b8\u00b9\7q\2\2\u00b9\u00ba\7p\2\2\u00ba\u00bb\7v\2\2"+
		"\u00bb\u00bc\7k\2\2\u00bc\u00bd\7p\2\2\u00bd\u00be\7w\2\2\u00be\u00bf"+
		"\7g\2\2\u00bf@\3\2\2\2\u00c0\u00c1\7h\2\2\u00c1\u00c2\7w\2\2\u00c2\u00c3"+
		"\7p\2\2\u00c3\u00c4\7e\2\2\u00c4\u00c5\7v\2\2\u00c5\u00c6\7k\2\2\u00c6"+
		"\u00c7\7q\2\2\u00c7\u00c8\7p\2\2\u00c8B\3\2\2\2\u00c9\u00ca\7v\2\2\u00ca"+
		"\u00cb\7j\2\2\u00cb\u00cc\7t\2\2\u00cc\u00cd\7g\2\2\u00cd\u00ce\7c\2\2"+
		"\u00ce\u00cf\7f\2\2\u00cfD\3\2\2\2\u00d0\u00d1\7t\2\2\u00d1\u00d2\7g\2"+
		"\2\u00d2\u00d3\7v\2\2\u00d3\u00d4\7w\2\2\u00d4\u00d5\7t\2\2\u00d5\u00d6"+
		"\7p\2\2\u00d6F\3\2\2\2\u00d7\u00d8\7p\2\2\u00d8\u00d9\7q\2\2\u00d9\u00da"+
		"\7v\2\2\u00daH\3\2\2\2\u00db\u00dc\7c\2\2\u00dc\u00dd\7p\2\2\u00dd\u00de"+
		"\7f\2\2\u00deJ\3\2\2\2\u00df\u00e0\7q\2\2\u00e0\u00e1\7t\2\2\u00e1L\3"+
		"\2\2\2\u00e2\u00e3\7v\2\2\u00e3\u00e4\7t\2\2\u00e4\u00e5\7w\2\2\u00e5"+
		"\u00e6\7g\2\2\u00e6N\3\2\2\2\u00e7\u00e8\7h\2\2\u00e8\u00e9\7c\2\2\u00e9"+
		"\u00ea\7n\2\2\u00ea\u00eb\7u\2\2\u00eb\u00ec\7g\2\2\u00ecP\3\2\2\2\u00ed"+
		"\u00ee\7k\2\2\u00ee\u00ef\7p\2\2\u00ef\u00f0\7h\2\2\u00f0\u00f1\7k\2\2"+
		"\u00f1\u00f2\7p\2\2\u00f2\u00f3\7k\2\2\u00f3\u00f4\7v\2\2\u00f4\u00f5"+
		"\7g\2\2\u00f5R\3\2\2\2\u00f6\u00f7\7o\2\2\u00f7\u00f8\7w\2\2\u00f8\u00f9"+
		"\7n\2\2\u00f9\u00fa\7v\2\2\u00fa\u00fb\7k\2\2\u00fbT\3\2\2\2\u00fc\u00fd"+
		"\7o\2\2\u00fd\u00fe\7q\2\2\u00fe\u00ff\7p\2\2\u00ff\u0100\7k\2\2\u0100"+
		"\u0101\7v\2\2\u0101\u0102\7q\2\2\u0102\u0103\7t\2\2\u0103V\3\2\2\2\u0104"+
		"\u0105\7<\2\2\u0105\u0106\7<\2\2\u0106X\3\2\2\2\u0107\u010b\t\2\2\2\u0108"+
		"\u010a\t\3\2\2\u0109\u0108\3\2\2\2\u010a\u010d\3\2\2\2\u010b\u0109\3\2"+
		"\2\2\u010b\u010c\3\2\2\2\u010cZ\3\2\2\2\u010d\u010b\3\2\2\2\u010e\u0110"+
		"\t\4\2\2\u010f\u010e\3\2\2\2\u0110\u0111\3\2\2\2\u0111\u010f\3\2\2\2\u0111"+
		"\u0112\3\2\2\2\u0112\\\3\2\2\2\u0113\u0119\7$\2\2\u0114\u0115\7^\2\2\u0115"+
		"\u0118\13\2\2\2\u0116\u0118\n\5\2\2\u0117\u0114\3\2\2\2\u0117\u0116\3"+
		"\2\2\2\u0118\u011b\3\2\2\2\u0119\u0117\3\2\2\2\u0119\u011a\3\2\2\2\u011a"+
		"\u011c\3\2\2\2\u011b\u0119\3\2\2\2\u011c\u011d\7$\2\2\u011d^\3\2\2\2\u011e"+
		"\u011f\7\61\2\2\u011f\u0120\7\61\2\2\u0120\u0124\3\2\2\2\u0121\u0123\n"+
		"\6\2\2\u0122\u0121\3\2\2\2\u0123\u0126\3\2\2\2\u0124\u0122\3\2\2\2\u0124"+
		"\u0125\3\2\2\2\u0125\u0127\3\2\2\2\u0126\u0124\3\2\2\2\u0127\u0128\b\60"+
		"\2\2\u0128`\3\2\2\2\u0129\u012a\7\61\2\2\u012a\u012b\7,\2\2\u012b\u012f"+
		"\3\2\2\2\u012c\u012e\13\2\2\2\u012d\u012c\3\2\2\2\u012e\u0131\3\2\2\2"+
		"\u012f\u0130\3\2\2\2\u012f\u012d\3\2\2\2\u0130\u0132\3\2\2\2\u0131\u012f"+
		"\3\2\2\2\u0132\u0133\7,\2\2\u0133\u0134\7\61\2\2\u0134\u0135\3\2\2\2\u0135"+
		"\u0136\b\61\2\2\u0136b\3\2\2\2\u0137\u0139\t\7\2\2\u0138\u0137\3\2\2\2"+
		"\u0139\u013a\3\2\2\2\u013a\u0138\3\2\2\2\u013a\u013b\3\2\2\2\u013b\u013c"+
		"\3\2\2\2\u013c\u013d\b\62\2\2\u013dd\3\2\2\2\n\2\u010b\u0111\u0117\u0119"+
		"\u0124\u012f\u013a\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}