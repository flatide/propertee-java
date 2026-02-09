// Generated from ProperTee.g4 by ANTLR 4.9.3
package com.propertee.parser;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class ProperTeeParser extends Parser {
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
	public static final int
		RULE_root = 0, RULE_statement = 1, RULE_assignment = 2, RULE_lvalue = 3, 
		RULE_block = 4, RULE_ifStatement = 5, RULE_functionDef = 6, RULE_parameterList = 7, 
		RULE_parallelStmt = 8, RULE_monitorClause = 9, RULE_spawnStmt = 10, RULE_iterationStmt = 11, 
		RULE_flowControl = 12, RULE_expression = 13, RULE_access = 14, RULE_atom = 15, 
		RULE_functionCall = 16, RULE_objectLiteral = 17, RULE_objectEntry = 18, 
		RULE_objectKey = 19, RULE_arrayLiteral = 20, RULE_comparisonOp = 21;
	private static String[] makeRuleNames() {
		return new String[] {
			"root", "statement", "assignment", "lvalue", "block", "ifStatement", 
			"functionDef", "parameterList", "parallelStmt", "monitorClause", "spawnStmt", 
			"iterationStmt", "flowControl", "expression", "access", "atom", "functionCall", 
			"objectLiteral", "objectEntry", "objectKey", "arrayLiteral", "comparisonOp"
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

	@Override
	public String getGrammarFileName() { return "ProperTee.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public ProperTeeParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class RootContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(ProperTeeParser.EOF, 0); }
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public RootContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_root; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitRoot(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RootContext root() throws RecognitionException {
		RootContext _localctx = new RootContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_root);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(47);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__6) | (1L << T__12) | (1L << T__14) | (1L << K_IF) | (1L << K_LOOP) | (1L << K_BREAK) | (1L << K_CONTINUE) | (1L << K_FUNCTION) | (1L << K_SPAWN) | (1L << K_RETURN) | (1L << K_NOT) | (1L << K_TRUE) | (1L << K_FALSE) | (1L << K_MULTI) | (1L << GLOBAL_PREFIX) | (1L << ID) | (1L << INTEGER) | (1L << STRING))) != 0)) {
				{
				{
				setState(44);
				statement();
				}
				}
				setState(49);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(50);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StatementContext extends ParserRuleContext {
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
	 
		public StatementContext() { }
		public void copyFrom(StatementContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class FuncDefStmtContext extends StatementContext {
		public FunctionDefContext functionDef() {
			return getRuleContext(FunctionDefContext.class,0);
		}
		public FuncDefStmtContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitFuncDefStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IfStmtContext extends StatementContext {
		public IfStatementContext ifStatement() {
			return getRuleContext(IfStatementContext.class,0);
		}
		public IfStmtContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitIfStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class SpawnExecStmtContext extends StatementContext {
		public SpawnStmtContext spawnStmt() {
			return getRuleContext(SpawnStmtContext.class,0);
		}
		public SpawnExecStmtContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitSpawnExecStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ExprStmtContext extends StatementContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ExprStmtContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitExprStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IterStmtContext extends StatementContext {
		public IterationStmtContext iterationStmt() {
			return getRuleContext(IterationStmtContext.class,0);
		}
		public IterStmtContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitIterStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class AssignStmtContext extends StatementContext {
		public AssignmentContext assignment() {
			return getRuleContext(AssignmentContext.class,0);
		}
		public AssignStmtContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitAssignStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class FlowStmtContext extends StatementContext {
		public FlowControlContext flowControl() {
			return getRuleContext(FlowControlContext.class,0);
		}
		public FlowStmtContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitFlowStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ParallelExecStmtContext extends StatementContext {
		public ParallelStmtContext parallelStmt() {
			return getRuleContext(ParallelStmtContext.class,0);
		}
		public ParallelExecStmtContext(StatementContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitParallelExecStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_statement);
		try {
			setState(60);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
			case 1:
				_localctx = new AssignStmtContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(52);
				assignment();
				}
				break;
			case 2:
				_localctx = new IfStmtContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(53);
				ifStatement();
				}
				break;
			case 3:
				_localctx = new IterStmtContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(54);
				iterationStmt();
				}
				break;
			case 4:
				_localctx = new FuncDefStmtContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(55);
				functionDef();
				}
				break;
			case 5:
				_localctx = new ParallelExecStmtContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(56);
				parallelStmt();
				}
				break;
			case 6:
				_localctx = new SpawnExecStmtContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(57);
				spawnStmt();
				}
				break;
			case 7:
				_localctx = new FlowStmtContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(58);
				flowControl();
				}
				break;
			case 8:
				_localctx = new ExprStmtContext(_localctx);
				enterOuterAlt(_localctx, 8);
				{
				setState(59);
				expression(0);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssignmentContext extends ParserRuleContext {
		public LvalueContext lvalue() {
			return getRuleContext(LvalueContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public AssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignment; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitAssignment(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AssignmentContext assignment() throws RecognitionException {
		AssignmentContext _localctx = new AssignmentContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(62);
			lvalue(0);
			setState(63);
			match(T__0);
			setState(64);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LvalueContext extends ParserRuleContext {
		public LvalueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_lvalue; }
	 
		public LvalueContext() { }
		public void copyFrom(LvalueContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class VarLValueContext extends LvalueContext {
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public VarLValueContext(LvalueContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitVarLValue(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class GlobalVarLValueContext extends LvalueContext {
		public TerminalNode GLOBAL_PREFIX() { return getToken(ProperTeeParser.GLOBAL_PREFIX, 0); }
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public GlobalVarLValueContext(LvalueContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitGlobalVarLValue(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class PropLValueContext extends LvalueContext {
		public LvalueContext lvalue() {
			return getRuleContext(LvalueContext.class,0);
		}
		public AccessContext access() {
			return getRuleContext(AccessContext.class,0);
		}
		public PropLValueContext(LvalueContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitPropLValue(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LvalueContext lvalue() throws RecognitionException {
		return lvalue(0);
	}

	private LvalueContext lvalue(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		LvalueContext _localctx = new LvalueContext(_ctx, _parentState);
		LvalueContext _prevctx = _localctx;
		int _startState = 6;
		enterRecursionRule(_localctx, 6, RULE_lvalue, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(70);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ID:
				{
				_localctx = new VarLValueContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(67);
				match(ID);
				}
				break;
			case GLOBAL_PREFIX:
				{
				_localctx = new GlobalVarLValueContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(68);
				match(GLOBAL_PREFIX);
				setState(69);
				match(ID);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(77);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new PropLValueContext(new LvalueContext(_parentctx, _parentState));
					pushNewRecursionContext(_localctx, _startState, RULE_lvalue);
					setState(72);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(73);
					match(T__1);
					setState(74);
					access();
					}
					} 
				}
				setState(79);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,3,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class BlockContext extends ParserRuleContext {
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(83);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__6) | (1L << T__12) | (1L << T__14) | (1L << K_IF) | (1L << K_LOOP) | (1L << K_BREAK) | (1L << K_CONTINUE) | (1L << K_FUNCTION) | (1L << K_SPAWN) | (1L << K_RETURN) | (1L << K_NOT) | (1L << K_TRUE) | (1L << K_FALSE) | (1L << K_MULTI) | (1L << GLOBAL_PREFIX) | (1L << ID) | (1L << INTEGER) | (1L << STRING))) != 0)) {
				{
				{
				setState(80);
				statement();
				}
				}
				setState(85);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IfStatementContext extends ParserRuleContext {
		public ExpressionContext condition;
		public BlockContext thenBody;
		public BlockContext elseBody;
		public TerminalNode K_IF() { return getToken(ProperTeeParser.K_IF, 0); }
		public TerminalNode K_THEN() { return getToken(ProperTeeParser.K_THEN, 0); }
		public TerminalNode K_END() { return getToken(ProperTeeParser.K_END, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public List<BlockContext> block() {
			return getRuleContexts(BlockContext.class);
		}
		public BlockContext block(int i) {
			return getRuleContext(BlockContext.class,i);
		}
		public TerminalNode K_ELSE() { return getToken(ProperTeeParser.K_ELSE, 0); }
		public IfStatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ifStatement; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitIfStatement(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IfStatementContext ifStatement() throws RecognitionException {
		IfStatementContext _localctx = new IfStatementContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_ifStatement);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(86);
			match(K_IF);
			setState(87);
			((IfStatementContext)_localctx).condition = expression(0);
			setState(88);
			match(K_THEN);
			setState(89);
			((IfStatementContext)_localctx).thenBody = block();
			setState(92);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==K_ELSE) {
				{
				setState(90);
				match(K_ELSE);
				setState(91);
				((IfStatementContext)_localctx).elseBody = block();
				}
			}

			setState(94);
			match(K_END);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionDefContext extends ParserRuleContext {
		public Token funcName;
		public TerminalNode K_FUNCTION() { return getToken(ProperTeeParser.K_FUNCTION, 0); }
		public TerminalNode K_DO() { return getToken(ProperTeeParser.K_DO, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode K_END() { return getToken(ProperTeeParser.K_END, 0); }
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public ParameterListContext parameterList() {
			return getRuleContext(ParameterListContext.class,0);
		}
		public FunctionDefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionDef; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitFunctionDef(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionDefContext functionDef() throws RecognitionException {
		FunctionDefContext _localctx = new FunctionDefContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_functionDef);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(96);
			match(K_FUNCTION);
			setState(97);
			((FunctionDefContext)_localctx).funcName = match(ID);
			setState(98);
			match(T__2);
			setState(100);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ID) {
				{
				setState(99);
				parameterList();
				}
			}

			setState(102);
			match(T__3);
			setState(103);
			match(K_DO);
			setState(104);
			block();
			setState(105);
			match(K_END);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ParameterListContext extends ParserRuleContext {
		public List<TerminalNode> ID() { return getTokens(ProperTeeParser.ID); }
		public TerminalNode ID(int i) {
			return getToken(ProperTeeParser.ID, i);
		}
		public ParameterListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parameterList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitParameterList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParameterListContext parameterList() throws RecognitionException {
		ParameterListContext _localctx = new ParameterListContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_parameterList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(107);
			match(ID);
			setState(112);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__4) {
				{
				{
				setState(108);
				match(T__4);
				setState(109);
				match(ID);
				}
				}
				setState(114);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ParallelStmtContext extends ParserRuleContext {
		public Token resultVar;
		public TerminalNode K_MULTI() { return getToken(ProperTeeParser.K_MULTI, 0); }
		public TerminalNode K_DO() { return getToken(ProperTeeParser.K_DO, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode K_END() { return getToken(ProperTeeParser.K_END, 0); }
		public MonitorClauseContext monitorClause() {
			return getRuleContext(MonitorClauseContext.class,0);
		}
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public ParallelStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_parallelStmt; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitParallelStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParallelStmtContext parallelStmt() throws RecognitionException {
		ParallelStmtContext _localctx = new ParallelStmtContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_parallelStmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(115);
			match(K_MULTI);
			setState(117);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ID) {
				{
				setState(116);
				((ParallelStmtContext)_localctx).resultVar = match(ID);
				}
			}

			setState(119);
			match(K_DO);
			setState(120);
			block();
			setState(122);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==K_MONITOR) {
				{
				setState(121);
				monitorClause();
				}
			}

			setState(124);
			match(K_END);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class MonitorClauseContext extends ParserRuleContext {
		public TerminalNode K_MONITOR() { return getToken(ProperTeeParser.K_MONITOR, 0); }
		public TerminalNode INTEGER() { return getToken(ProperTeeParser.INTEGER, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public MonitorClauseContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_monitorClause; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitMonitorClause(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MonitorClauseContext monitorClause() throws RecognitionException {
		MonitorClauseContext _localctx = new MonitorClauseContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_monitorClause);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(126);
			match(K_MONITOR);
			setState(127);
			match(INTEGER);
			setState(128);
			block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SpawnStmtContext extends ParserRuleContext {
		public SpawnStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_spawnStmt; }
	 
		public SpawnStmtContext() { }
		public void copyFrom(SpawnStmtContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class SpawnKeyStmtContext extends SpawnStmtContext {
		public TerminalNode K_SPAWN() { return getToken(ProperTeeParser.K_SPAWN, 0); }
		public FunctionCallContext functionCall() {
			return getRuleContext(FunctionCallContext.class,0);
		}
		public AccessContext access() {
			return getRuleContext(AccessContext.class,0);
		}
		public SpawnKeyStmtContext(SpawnStmtContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitSpawnKeyStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final SpawnStmtContext spawnStmt() throws RecognitionException {
		SpawnStmtContext _localctx = new SpawnStmtContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_spawnStmt);
		int _la;
		try {
			_localctx = new SpawnKeyStmtContext(_localctx);
			enterOuterAlt(_localctx, 1);
			{
			setState(130);
			match(K_SPAWN);
			setState(132);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__11) | (1L << ID) | (1L << INTEGER) | (1L << STRING))) != 0)) {
				{
				setState(131);
				access();
				}
			}

			setState(134);
			match(T__5);
			setState(135);
			functionCall();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IterationStmtContext extends ParserRuleContext {
		public IterationStmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_iterationStmt; }
	 
		public IterationStmtContext() { }
		public void copyFrom(IterationStmtContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class KeyValueLoopContext extends IterationStmtContext {
		public Token key;
		public Token value;
		public TerminalNode K_LOOP() { return getToken(ProperTeeParser.K_LOOP, 0); }
		public TerminalNode K_IN() { return getToken(ProperTeeParser.K_IN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode K_DO() { return getToken(ProperTeeParser.K_DO, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode K_END() { return getToken(ProperTeeParser.K_END, 0); }
		public List<TerminalNode> ID() { return getTokens(ProperTeeParser.ID); }
		public TerminalNode ID(int i) {
			return getToken(ProperTeeParser.ID, i);
		}
		public TerminalNode K_INFINITE() { return getToken(ProperTeeParser.K_INFINITE, 0); }
		public KeyValueLoopContext(IterationStmtContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitKeyValueLoop(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ConditionLoopContext extends IterationStmtContext {
		public TerminalNode K_LOOP() { return getToken(ProperTeeParser.K_LOOP, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode K_DO() { return getToken(ProperTeeParser.K_DO, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode K_END() { return getToken(ProperTeeParser.K_END, 0); }
		public TerminalNode K_INFINITE() { return getToken(ProperTeeParser.K_INFINITE, 0); }
		public ConditionLoopContext(IterationStmtContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitConditionLoop(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ValueLoopContext extends IterationStmtContext {
		public Token value;
		public TerminalNode K_LOOP() { return getToken(ProperTeeParser.K_LOOP, 0); }
		public TerminalNode K_IN() { return getToken(ProperTeeParser.K_IN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode K_DO() { return getToken(ProperTeeParser.K_DO, 0); }
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public TerminalNode K_END() { return getToken(ProperTeeParser.K_END, 0); }
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public TerminalNode K_INFINITE() { return getToken(ProperTeeParser.K_INFINITE, 0); }
		public ValueLoopContext(IterationStmtContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitValueLoop(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IterationStmtContext iterationStmt() throws RecognitionException {
		IterationStmtContext _localctx = new IterationStmtContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_iterationStmt);
		int _la;
		try {
			setState(170);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,14,_ctx) ) {
			case 1:
				_localctx = new ConditionLoopContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(137);
				match(K_LOOP);
				setState(138);
				expression(0);
				setState(140);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==K_INFINITE) {
					{
					setState(139);
					match(K_INFINITE);
					}
				}

				setState(142);
				match(K_DO);
				setState(143);
				block();
				setState(144);
				match(K_END);
				}
				break;
			case 2:
				_localctx = new ValueLoopContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(146);
				match(K_LOOP);
				setState(147);
				((ValueLoopContext)_localctx).value = match(ID);
				setState(148);
				match(K_IN);
				setState(149);
				expression(0);
				setState(151);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==K_INFINITE) {
					{
					setState(150);
					match(K_INFINITE);
					}
				}

				setState(153);
				match(K_DO);
				setState(154);
				block();
				setState(155);
				match(K_END);
				}
				break;
			case 3:
				_localctx = new KeyValueLoopContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(157);
				match(K_LOOP);
				setState(158);
				((KeyValueLoopContext)_localctx).key = match(ID);
				setState(159);
				match(T__4);
				setState(160);
				((KeyValueLoopContext)_localctx).value = match(ID);
				setState(161);
				match(K_IN);
				setState(162);
				expression(0);
				setState(164);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==K_INFINITE) {
					{
					setState(163);
					match(K_INFINITE);
					}
				}

				setState(166);
				match(K_DO);
				setState(167);
				block();
				setState(168);
				match(K_END);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FlowControlContext extends ParserRuleContext {
		public FlowControlContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_flowControl; }
	 
		public FlowControlContext() { }
		public void copyFrom(FlowControlContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class ContinueStmtContext extends FlowControlContext {
		public TerminalNode K_CONTINUE() { return getToken(ProperTeeParser.K_CONTINUE, 0); }
		public ContinueStmtContext(FlowControlContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitContinueStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BreakStmtContext extends FlowControlContext {
		public TerminalNode K_BREAK() { return getToken(ProperTeeParser.K_BREAK, 0); }
		public BreakStmtContext(FlowControlContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitBreakStmt(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ReturnStmtContext extends FlowControlContext {
		public TerminalNode K_RETURN() { return getToken(ProperTeeParser.K_RETURN, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ReturnStmtContext(FlowControlContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitReturnStmt(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FlowControlContext flowControl() throws RecognitionException {
		FlowControlContext _localctx = new FlowControlContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_flowControl);
		try {
			setState(178);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case K_BREAK:
				_localctx = new BreakStmtContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(172);
				match(K_BREAK);
				}
				break;
			case K_CONTINUE:
				_localctx = new ContinueStmtContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(173);
				match(K_CONTINUE);
				}
				break;
			case K_RETURN:
				_localctx = new ReturnStmtContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(174);
				match(K_RETURN);
				setState(176);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
				case 1:
					{
					setState(175);
					expression(0);
					}
					break;
				}
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionContext extends ParserRuleContext {
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	 
		public ExpressionContext() { }
		public void copyFrom(ExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class AndExprContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode K_AND() { return getToken(ProperTeeParser.K_AND, 0); }
		public AndExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitAndExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class MultiplicativeExprContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public MultiplicativeExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitMultiplicativeExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class AdditiveExprContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public AdditiveExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitAdditiveExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ComparisonExprContext extends ExpressionContext {
		public ComparisonOpContext op;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ComparisonOpContext comparisonOp() {
			return getRuleContext(ComparisonOpContext.class,0);
		}
		public ComparisonExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitComparisonExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NotExprContext extends ExpressionContext {
		public TerminalNode K_NOT() { return getToken(ProperTeeParser.K_NOT, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NotExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitNotExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class AtomExprContext extends ExpressionContext {
		public AtomContext atom() {
			return getRuleContext(AtomContext.class,0);
		}
		public AtomExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitAtomExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class MemberAccessExprContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public AccessContext access() {
			return getRuleContext(AccessContext.class,0);
		}
		public MemberAccessExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitMemberAccessExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class OrExprContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TerminalNode K_OR() { return getToken(ProperTeeParser.K_OR, 0); }
		public OrExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitOrExpr(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class UnaryMinusExprContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public UnaryMinusExprContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitUnaryMinusExpr(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		return expression(0);
	}

	private ExpressionContext expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState);
		ExpressionContext _prevctx = _localctx;
		int _startState = 26;
		enterRecursionRule(_localctx, 26, RULE_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(186);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
			case T__12:
			case T__14:
			case K_TRUE:
			case K_FALSE:
			case GLOBAL_PREFIX:
			case ID:
			case INTEGER:
			case STRING:
				{
				_localctx = new AtomExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(181);
				atom();
				}
				break;
			case T__6:
				{
				_localctx = new UnaryMinusExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(182);
				match(T__6);
				setState(183);
				expression(7);
				}
				break;
			case K_NOT:
				{
				_localctx = new NotExprContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(184);
				match(K_NOT);
				setState(185);
				expression(6);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(209);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,19,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(207);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
					case 1:
						{
						_localctx = new MultiplicativeExprContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(188);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(189);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__7) | (1L << T__8) | (1L << T__9))) != 0)) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(190);
						expression(6);
						}
						break;
					case 2:
						{
						_localctx = new AdditiveExprContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(191);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(192);
						_la = _input.LA(1);
						if ( !(_la==T__6 || _la==T__10) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(193);
						expression(5);
						}
						break;
					case 3:
						{
						_localctx = new ComparisonExprContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(194);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(195);
						((ComparisonExprContext)_localctx).op = comparisonOp();
						setState(196);
						expression(4);
						}
						break;
					case 4:
						{
						_localctx = new AndExprContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(198);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(199);
						match(K_AND);
						setState(200);
						expression(3);
						}
						break;
					case 5:
						{
						_localctx = new OrExprContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(201);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(202);
						match(K_OR);
						setState(203);
						expression(2);
						}
						break;
					case 6:
						{
						_localctx = new MemberAccessExprContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(204);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(205);
						match(T__1);
						setState(206);
						access();
						}
						break;
					}
					} 
				}
				setState(211);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,19,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class AccessContext extends ParserRuleContext {
		public AccessContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_access; }
	 
		public AccessContext() { }
		public void copyFrom(AccessContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class StaticAccessContext extends AccessContext {
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public StaticAccessContext(AccessContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitStaticAccess(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ArrayAccessContext extends AccessContext {
		public TerminalNode INTEGER() { return getToken(ProperTeeParser.INTEGER, 0); }
		public ArrayAccessContext(AccessContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitArrayAccess(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class VarEvalAccessContext extends AccessContext {
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public VarEvalAccessContext(AccessContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitVarEvalAccess(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class EvalAccessContext extends AccessContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public EvalAccessContext(AccessContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitEvalAccess(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class StringKeyAccessContext extends AccessContext {
		public TerminalNode STRING() { return getToken(ProperTeeParser.STRING, 0); }
		public StringKeyAccessContext(AccessContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitStringKeyAccess(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AccessContext access() throws RecognitionException {
		AccessContext _localctx = new AccessContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_access);
		try {
			setState(222);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
			case 1:
				_localctx = new StaticAccessContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(212);
				match(ID);
				}
				break;
			case 2:
				_localctx = new ArrayAccessContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(213);
				match(INTEGER);
				}
				break;
			case 3:
				_localctx = new StringKeyAccessContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(214);
				match(STRING);
				}
				break;
			case 4:
				_localctx = new VarEvalAccessContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(215);
				match(T__11);
				setState(216);
				match(ID);
				}
				break;
			case 5:
				_localctx = new EvalAccessContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(217);
				match(T__11);
				setState(218);
				match(T__2);
				setState(219);
				expression(0);
				setState(220);
				match(T__3);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AtomContext extends ParserRuleContext {
		public AtomContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_atom; }
	 
		public AtomContext() { }
		public void copyFrom(AtomContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class IntegerAtomContext extends AtomContext {
		public TerminalNode INTEGER() { return getToken(ProperTeeParser.INTEGER, 0); }
		public IntegerAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitIntegerAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ObjectAtomContext extends AtomContext {
		public ObjectLiteralContext objectLiteral() {
			return getRuleContext(ObjectLiteralContext.class,0);
		}
		public ObjectAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitObjectAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ArrayAtomContext extends AtomContext {
		public ArrayLiteralContext arrayLiteral() {
			return getRuleContext(ArrayLiteralContext.class,0);
		}
		public ArrayAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitArrayAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class GlobalVarReferenceContext extends AtomContext {
		public TerminalNode GLOBAL_PREFIX() { return getToken(ProperTeeParser.GLOBAL_PREFIX, 0); }
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public GlobalVarReferenceContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitGlobalVarReference(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class VarReferenceContext extends AtomContext {
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public VarReferenceContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitVarReference(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class StringAtomContext extends AtomContext {
		public TerminalNode STRING() { return getToken(ProperTeeParser.STRING, 0); }
		public StringAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitStringAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class FuncAtomContext extends AtomContext {
		public FunctionCallContext functionCall() {
			return getRuleContext(FunctionCallContext.class,0);
		}
		public FuncAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitFuncAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ParenAtomContext extends AtomContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ParenAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitParenAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class DecimalAtomContext extends AtomContext {
		public List<TerminalNode> INTEGER() { return getTokens(ProperTeeParser.INTEGER); }
		public TerminalNode INTEGER(int i) {
			return getToken(ProperTeeParser.INTEGER, i);
		}
		public DecimalAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitDecimalAtom(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BooleanAtomContext extends AtomContext {
		public TerminalNode K_TRUE() { return getToken(ProperTeeParser.K_TRUE, 0); }
		public TerminalNode K_FALSE() { return getToken(ProperTeeParser.K_FALSE, 0); }
		public BooleanAtomContext(AtomContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitBooleanAtom(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AtomContext atom() throws RecognitionException {
		AtomContext _localctx = new AtomContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_atom);
		int _la;
		try {
			setState(240);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				_localctx = new FuncAtomContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(224);
				functionCall();
				}
				break;
			case 2:
				_localctx = new GlobalVarReferenceContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(225);
				match(GLOBAL_PREFIX);
				setState(226);
				match(ID);
				}
				break;
			case 3:
				_localctx = new VarReferenceContext(_localctx);
				enterOuterAlt(_localctx, 3);
				{
				setState(227);
				match(ID);
				}
				break;
			case 4:
				_localctx = new DecimalAtomContext(_localctx);
				enterOuterAlt(_localctx, 4);
				{
				setState(228);
				match(INTEGER);
				setState(229);
				match(T__1);
				setState(230);
				match(INTEGER);
				}
				break;
			case 5:
				_localctx = new IntegerAtomContext(_localctx);
				enterOuterAlt(_localctx, 5);
				{
				setState(231);
				match(INTEGER);
				}
				break;
			case 6:
				_localctx = new StringAtomContext(_localctx);
				enterOuterAlt(_localctx, 6);
				{
				setState(232);
				match(STRING);
				}
				break;
			case 7:
				_localctx = new BooleanAtomContext(_localctx);
				enterOuterAlt(_localctx, 7);
				{
				setState(233);
				_la = _input.LA(1);
				if ( !(_la==K_TRUE || _la==K_FALSE) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				}
				break;
			case 8:
				_localctx = new ObjectAtomContext(_localctx);
				enterOuterAlt(_localctx, 8);
				{
				setState(234);
				objectLiteral();
				}
				break;
			case 9:
				_localctx = new ArrayAtomContext(_localctx);
				enterOuterAlt(_localctx, 9);
				{
				setState(235);
				arrayLiteral();
				}
				break;
			case 10:
				_localctx = new ParenAtomContext(_localctx);
				enterOuterAlt(_localctx, 10);
				{
				setState(236);
				match(T__2);
				setState(237);
				expression(0);
				setState(238);
				match(T__3);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionCallContext extends ParserRuleContext {
		public Token funcName;
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public FunctionCallContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionCall; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitFunctionCall(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionCallContext functionCall() throws RecognitionException {
		FunctionCallContext _localctx = new FunctionCallContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_functionCall);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(242);
			((FunctionCallContext)_localctx).funcName = match(ID);
			setState(243);
			match(T__2);
			setState(252);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__6) | (1L << T__12) | (1L << T__14) | (1L << K_NOT) | (1L << K_TRUE) | (1L << K_FALSE) | (1L << GLOBAL_PREFIX) | (1L << ID) | (1L << INTEGER) | (1L << STRING))) != 0)) {
				{
				setState(244);
				expression(0);
				setState(249);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__4) {
					{
					{
					setState(245);
					match(T__4);
					setState(246);
					expression(0);
					}
					}
					setState(251);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(254);
			match(T__3);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ObjectLiteralContext extends ParserRuleContext {
		public List<ObjectEntryContext> objectEntry() {
			return getRuleContexts(ObjectEntryContext.class);
		}
		public ObjectEntryContext objectEntry(int i) {
			return getRuleContext(ObjectEntryContext.class,i);
		}
		public ObjectLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitObjectLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectLiteralContext objectLiteral() throws RecognitionException {
		ObjectLiteralContext _localctx = new ObjectLiteralContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_objectLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(256);
			match(T__12);
			setState(265);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ID) | (1L << INTEGER) | (1L << STRING))) != 0)) {
				{
				setState(257);
				objectEntry();
				setState(262);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__4) {
					{
					{
					setState(258);
					match(T__4);
					setState(259);
					objectEntry();
					}
					}
					setState(264);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(267);
			match(T__13);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ObjectEntryContext extends ParserRuleContext {
		public ObjectKeyContext objectKey() {
			return getRuleContext(ObjectKeyContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ObjectEntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectEntry; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitObjectEntry(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectEntryContext objectEntry() throws RecognitionException {
		ObjectEntryContext _localctx = new ObjectEntryContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_objectEntry);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(269);
			objectKey();
			setState(270);
			match(T__5);
			setState(271);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ObjectKeyContext extends ParserRuleContext {
		public TerminalNode ID() { return getToken(ProperTeeParser.ID, 0); }
		public TerminalNode STRING() { return getToken(ProperTeeParser.STRING, 0); }
		public TerminalNode INTEGER() { return getToken(ProperTeeParser.INTEGER, 0); }
		public ObjectKeyContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_objectKey; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitObjectKey(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ObjectKeyContext objectKey() throws RecognitionException {
		ObjectKeyContext _localctx = new ObjectKeyContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_objectKey);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(273);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << ID) | (1L << INTEGER) | (1L << STRING))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArrayLiteralContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ArrayLiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayLiteral; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitArrayLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArrayLiteralContext arrayLiteral() throws RecognitionException {
		ArrayLiteralContext _localctx = new ArrayLiteralContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_arrayLiteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(275);
			match(T__14);
			setState(284);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__6) | (1L << T__12) | (1L << T__14) | (1L << K_NOT) | (1L << K_TRUE) | (1L << K_FALSE) | (1L << GLOBAL_PREFIX) | (1L << ID) | (1L << INTEGER) | (1L << STRING))) != 0)) {
				{
				setState(276);
				expression(0);
				setState(281);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__4) {
					{
					{
					setState(277);
					match(T__4);
					setState(278);
					expression(0);
					}
					}
					setState(283);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
			}

			setState(286);
			match(T__15);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ComparisonOpContext extends ParserRuleContext {
		public ComparisonOpContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_comparisonOp; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ProperTeeVisitor ) return ((ProperTeeVisitor<? extends T>)visitor).visitComparisonOp(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ComparisonOpContext comparisonOp() throws RecognitionException {
		ComparisonOpContext _localctx = new ComparisonOpContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_comparisonOp);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(288);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 3:
			return lvalue_sempred((LvalueContext)_localctx, predIndex);
		case 13:
			return expression_sempred((ExpressionContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean lvalue_sempred(LvalueContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 1);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return precpred(_ctx, 5);
		case 2:
			return precpred(_ctx, 4);
		case 3:
			return precpred(_ctx, 3);
		case 4:
			return precpred(_ctx, 2);
		case 5:
			return precpred(_ctx, 1);
		case 6:
			return precpred(_ctx, 8);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\63\u0125\4\2\t\2"+
		"\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\3\2\7\2\60\n\2\f\2"+
		"\16\2\63\13\2\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3?\n\3\3\4\3\4"+
		"\3\4\3\4\3\5\3\5\3\5\3\5\5\5I\n\5\3\5\3\5\3\5\7\5N\n\5\f\5\16\5Q\13\5"+
		"\3\6\7\6T\n\6\f\6\16\6W\13\6\3\7\3\7\3\7\3\7\3\7\3\7\5\7_\n\7\3\7\3\7"+
		"\3\b\3\b\3\b\3\b\5\bg\n\b\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t\7\tq\n\t\f\t"+
		"\16\tt\13\t\3\n\3\n\5\nx\n\n\3\n\3\n\3\n\5\n}\n\n\3\n\3\n\3\13\3\13\3"+
		"\13\3\13\3\f\3\f\5\f\u0087\n\f\3\f\3\f\3\f\3\r\3\r\3\r\5\r\u008f\n\r\3"+
		"\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\3\r\5\r\u009a\n\r\3\r\3\r\3\r\3\r\3\r\3"+
		"\r\3\r\3\r\3\r\3\r\3\r\5\r\u00a7\n\r\3\r\3\r\3\r\3\r\5\r\u00ad\n\r\3\16"+
		"\3\16\3\16\3\16\5\16\u00b3\n\16\5\16\u00b5\n\16\3\17\3\17\3\17\3\17\3"+
		"\17\3\17\5\17\u00bd\n\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17"+
		"\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\3\17\7\17\u00d2\n\17\f\17"+
		"\16\17\u00d5\13\17\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\3\20\5"+
		"\20\u00e1\n\20\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21\3\21"+
		"\3\21\3\21\3\21\3\21\3\21\5\21\u00f3\n\21\3\22\3\22\3\22\3\22\3\22\7\22"+
		"\u00fa\n\22\f\22\16\22\u00fd\13\22\5\22\u00ff\n\22\3\22\3\22\3\23\3\23"+
		"\3\23\3\23\7\23\u0107\n\23\f\23\16\23\u010a\13\23\5\23\u010c\n\23\3\23"+
		"\3\23\3\24\3\24\3\24\3\24\3\25\3\25\3\26\3\26\3\26\3\26\7\26\u011a\n\26"+
		"\f\26\16\26\u011d\13\26\5\26\u011f\n\26\3\26\3\26\3\27\3\27\3\27\2\4\b"+
		"\34\30\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(*,\2\7\3\2\n\f\4\2"+
		"\t\t\r\r\3\2()\3\2.\60\3\2\23\30\2\u0142\2\61\3\2\2\2\4>\3\2\2\2\6@\3"+
		"\2\2\2\bH\3\2\2\2\nU\3\2\2\2\fX\3\2\2\2\16b\3\2\2\2\20m\3\2\2\2\22u\3"+
		"\2\2\2\24\u0080\3\2\2\2\26\u0084\3\2\2\2\30\u00ac\3\2\2\2\32\u00b4\3\2"+
		"\2\2\34\u00bc\3\2\2\2\36\u00e0\3\2\2\2 \u00f2\3\2\2\2\"\u00f4\3\2\2\2"+
		"$\u0102\3\2\2\2&\u010f\3\2\2\2(\u0113\3\2\2\2*\u0115\3\2\2\2,\u0122\3"+
		"\2\2\2.\60\5\4\3\2/.\3\2\2\2\60\63\3\2\2\2\61/\3\2\2\2\61\62\3\2\2\2\62"+
		"\64\3\2\2\2\63\61\3\2\2\2\64\65\7\2\2\3\65\3\3\2\2\2\66?\5\6\4\2\67?\5"+
		"\f\7\28?\5\30\r\29?\5\16\b\2:?\5\22\n\2;?\5\26\f\2<?\5\32\16\2=?\5\34"+
		"\17\2>\66\3\2\2\2>\67\3\2\2\2>8\3\2\2\2>9\3\2\2\2>:\3\2\2\2>;\3\2\2\2"+
		"><\3\2\2\2>=\3\2\2\2?\5\3\2\2\2@A\5\b\5\2AB\7\3\2\2BC\5\34\17\2C\7\3\2"+
		"\2\2DE\b\5\1\2EI\7.\2\2FG\7-\2\2GI\7.\2\2HD\3\2\2\2HF\3\2\2\2IO\3\2\2"+
		"\2JK\f\3\2\2KL\7\4\2\2LN\5\36\20\2MJ\3\2\2\2NQ\3\2\2\2OM\3\2\2\2OP\3\2"+
		"\2\2P\t\3\2\2\2QO\3\2\2\2RT\5\4\3\2SR\3\2\2\2TW\3\2\2\2US\3\2\2\2UV\3"+
		"\2\2\2V\13\3\2\2\2WU\3\2\2\2XY\7\31\2\2YZ\5\34\17\2Z[\7\32\2\2[^\5\n\6"+
		"\2\\]\7\33\2\2]_\5\n\6\2^\\\3\2\2\2^_\3\2\2\2_`\3\2\2\2`a\7\34\2\2a\r"+
		"\3\2\2\2bc\7\"\2\2cd\7.\2\2df\7\5\2\2eg\5\20\t\2fe\3\2\2\2fg\3\2\2\2g"+
		"h\3\2\2\2hi\7\6\2\2ij\7\37\2\2jk\5\n\6\2kl\7\34\2\2l\17\3\2\2\2mr\7.\2"+
		"\2no\7\7\2\2oq\7.\2\2pn\3\2\2\2qt\3\2\2\2rp\3\2\2\2rs\3\2\2\2s\21\3\2"+
		"\2\2tr\3\2\2\2uw\7+\2\2vx\7.\2\2wv\3\2\2\2wx\3\2\2\2xy\3\2\2\2yz\7\37"+
		"\2\2z|\5\n\6\2{}\5\24\13\2|{\3\2\2\2|}\3\2\2\2}~\3\2\2\2~\177\7\34\2\2"+
		"\177\23\3\2\2\2\u0080\u0081\7,\2\2\u0081\u0082\7/\2\2\u0082\u0083\5\n"+
		"\6\2\u0083\25\3\2\2\2\u0084\u0086\7#\2\2\u0085\u0087\5\36\20\2\u0086\u0085"+
		"\3\2\2\2\u0086\u0087\3\2\2\2\u0087\u0088\3\2\2\2\u0088\u0089\7\b\2\2\u0089"+
		"\u008a\5\"\22\2\u008a\27\3\2\2\2\u008b\u008c\7\35\2\2\u008c\u008e\5\34"+
		"\17\2\u008d\u008f\7*\2\2\u008e\u008d\3\2\2\2\u008e\u008f\3\2\2\2\u008f"+
		"\u0090\3\2\2\2\u0090\u0091\7\37\2\2\u0091\u0092\5\n\6\2\u0092\u0093\7"+
		"\34\2\2\u0093\u00ad\3\2\2\2\u0094\u0095\7\35\2\2\u0095\u0096\7.\2\2\u0096"+
		"\u0097\7\36\2\2\u0097\u0099\5\34\17\2\u0098\u009a\7*\2\2\u0099\u0098\3"+
		"\2\2\2\u0099\u009a\3\2\2\2\u009a\u009b\3\2\2\2\u009b\u009c\7\37\2\2\u009c"+
		"\u009d\5\n\6\2\u009d\u009e\7\34\2\2\u009e\u00ad\3\2\2\2\u009f\u00a0\7"+
		"\35\2\2\u00a0\u00a1\7.\2\2\u00a1\u00a2\7\7\2\2\u00a2\u00a3\7.\2\2\u00a3"+
		"\u00a4\7\36\2\2\u00a4\u00a6\5\34\17\2\u00a5\u00a7\7*\2\2\u00a6\u00a5\3"+
		"\2\2\2\u00a6\u00a7\3\2\2\2\u00a7\u00a8\3\2\2\2\u00a8\u00a9\7\37\2\2\u00a9"+
		"\u00aa\5\n\6\2\u00aa\u00ab\7\34\2\2\u00ab\u00ad\3\2\2\2\u00ac\u008b\3"+
		"\2\2\2\u00ac\u0094\3\2\2\2\u00ac\u009f\3\2\2\2\u00ad\31\3\2\2\2\u00ae"+
		"\u00b5\7 \2\2\u00af\u00b5\7!\2\2\u00b0\u00b2\7$\2\2\u00b1\u00b3\5\34\17"+
		"\2\u00b2\u00b1\3\2\2\2\u00b2\u00b3\3\2\2\2\u00b3\u00b5\3\2\2\2\u00b4\u00ae"+
		"\3\2\2\2\u00b4\u00af\3\2\2\2\u00b4\u00b0\3\2\2\2\u00b5\33\3\2\2\2\u00b6"+
		"\u00b7\b\17\1\2\u00b7\u00bd\5 \21\2\u00b8\u00b9\7\t\2\2\u00b9\u00bd\5"+
		"\34\17\t\u00ba\u00bb\7%\2\2\u00bb\u00bd\5\34\17\b\u00bc\u00b6\3\2\2\2"+
		"\u00bc\u00b8\3\2\2\2\u00bc\u00ba\3\2\2\2\u00bd\u00d3\3\2\2\2\u00be\u00bf"+
		"\f\7\2\2\u00bf\u00c0\t\2\2\2\u00c0\u00d2\5\34\17\b\u00c1\u00c2\f\6\2\2"+
		"\u00c2\u00c3\t\3\2\2\u00c3\u00d2\5\34\17\7\u00c4\u00c5\f\5\2\2\u00c5\u00c6"+
		"\5,\27\2\u00c6\u00c7\5\34\17\6\u00c7\u00d2\3\2\2\2\u00c8\u00c9\f\4\2\2"+
		"\u00c9\u00ca\7&\2\2\u00ca\u00d2\5\34\17\5\u00cb\u00cc\f\3\2\2\u00cc\u00cd"+
		"\7\'\2\2\u00cd\u00d2\5\34\17\4\u00ce\u00cf\f\n\2\2\u00cf\u00d0\7\4\2\2"+
		"\u00d0\u00d2\5\36\20\2\u00d1\u00be\3\2\2\2\u00d1\u00c1\3\2\2\2\u00d1\u00c4"+
		"\3\2\2\2\u00d1\u00c8\3\2\2\2\u00d1\u00cb\3\2\2\2\u00d1\u00ce\3\2\2\2\u00d2"+
		"\u00d5\3\2\2\2\u00d3\u00d1\3\2\2\2\u00d3\u00d4\3\2\2\2\u00d4\35\3\2\2"+
		"\2\u00d5\u00d3\3\2\2\2\u00d6\u00e1\7.\2\2\u00d7\u00e1\7/\2\2\u00d8\u00e1"+
		"\7\60\2\2\u00d9\u00da\7\16\2\2\u00da\u00e1\7.\2\2\u00db\u00dc\7\16\2\2"+
		"\u00dc\u00dd\7\5\2\2\u00dd\u00de\5\34\17\2\u00de\u00df\7\6\2\2\u00df\u00e1"+
		"\3\2\2\2\u00e0\u00d6\3\2\2\2\u00e0\u00d7\3\2\2\2\u00e0\u00d8\3\2\2\2\u00e0"+
		"\u00d9\3\2\2\2\u00e0\u00db\3\2\2\2\u00e1\37\3\2\2\2\u00e2\u00f3\5\"\22"+
		"\2\u00e3\u00e4\7-\2\2\u00e4\u00f3\7.\2\2\u00e5\u00f3\7.\2\2\u00e6\u00e7"+
		"\7/\2\2\u00e7\u00e8\7\4\2\2\u00e8\u00f3\7/\2\2\u00e9\u00f3\7/\2\2\u00ea"+
		"\u00f3\7\60\2\2\u00eb\u00f3\t\4\2\2\u00ec\u00f3\5$\23\2\u00ed\u00f3\5"+
		"*\26\2\u00ee\u00ef\7\5\2\2\u00ef\u00f0\5\34\17\2\u00f0\u00f1\7\6\2\2\u00f1"+
		"\u00f3\3\2\2\2\u00f2\u00e2\3\2\2\2\u00f2\u00e3\3\2\2\2\u00f2\u00e5\3\2"+
		"\2\2\u00f2\u00e6\3\2\2\2\u00f2\u00e9\3\2\2\2\u00f2\u00ea\3\2\2\2\u00f2"+
		"\u00eb\3\2\2\2\u00f2\u00ec\3\2\2\2\u00f2\u00ed\3\2\2\2\u00f2\u00ee\3\2"+
		"\2\2\u00f3!\3\2\2\2\u00f4\u00f5\7.\2\2\u00f5\u00fe\7\5\2\2\u00f6\u00fb"+
		"\5\34\17\2\u00f7\u00f8\7\7\2\2\u00f8\u00fa\5\34\17\2\u00f9\u00f7\3\2\2"+
		"\2\u00fa\u00fd\3\2\2\2\u00fb\u00f9\3\2\2\2\u00fb\u00fc\3\2\2\2\u00fc\u00ff"+
		"\3\2\2\2\u00fd\u00fb\3\2\2\2\u00fe\u00f6\3\2\2\2\u00fe\u00ff\3\2\2\2\u00ff"+
		"\u0100\3\2\2\2\u0100\u0101\7\6\2\2\u0101#\3\2\2\2\u0102\u010b\7\17\2\2"+
		"\u0103\u0108\5&\24\2\u0104\u0105\7\7\2\2\u0105\u0107\5&\24\2\u0106\u0104"+
		"\3\2\2\2\u0107\u010a\3\2\2\2\u0108\u0106\3\2\2\2\u0108\u0109\3\2\2\2\u0109"+
		"\u010c\3\2\2\2\u010a\u0108\3\2\2\2\u010b\u0103\3\2\2\2\u010b\u010c\3\2"+
		"\2\2\u010c\u010d\3\2\2\2\u010d\u010e\7\20\2\2\u010e%\3\2\2\2\u010f\u0110"+
		"\5(\25\2\u0110\u0111\7\b\2\2\u0111\u0112\5\34\17\2\u0112\'\3\2\2\2\u0113"+
		"\u0114\t\5\2\2\u0114)\3\2\2\2\u0115\u011e\7\21\2\2\u0116\u011b\5\34\17"+
		"\2\u0117\u0118\7\7\2\2\u0118\u011a\5\34\17\2\u0119\u0117\3\2\2\2\u011a"+
		"\u011d\3\2\2\2\u011b\u0119\3\2\2\2\u011b\u011c\3\2\2\2\u011c\u011f\3\2"+
		"\2\2\u011d\u011b\3\2\2\2\u011e\u0116\3\2\2\2\u011e\u011f\3\2\2\2\u011f"+
		"\u0120\3\2\2\2\u0120\u0121\7\22\2\2\u0121+\3\2\2\2\u0122\u0123\t\6\2\2"+
		"\u0123-\3\2\2\2\36\61>HOU^frw|\u0086\u008e\u0099\u00a6\u00ac\u00b2\u00b4"+
		"\u00bc\u00d1\u00d3\u00e0\u00f2\u00fb\u00fe\u0108\u010b\u011b\u011e";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}