// Generated from ProperTee.g4 by ANTLR 4.9.3
package com.propertee.parser;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link ProperTeeParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface ProperTeeVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#root}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitRoot(ProperTeeParser.RootContext ctx);
	/**
	 * Visit a parse tree produced by the {@code AssignStmt}
	 * labeled alternative in {@link ProperTeeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignStmt(ProperTeeParser.AssignStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code IfStmt}
	 * labeled alternative in {@link ProperTeeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStmt(ProperTeeParser.IfStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code iterStmt}
	 * labeled alternative in {@link ProperTeeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIterStmt(ProperTeeParser.IterStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code FuncDefStmt}
	 * labeled alternative in {@link ProperTeeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncDefStmt(ProperTeeParser.FuncDefStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ParallelExecStmt}
	 * labeled alternative in {@link ProperTeeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParallelExecStmt(ProperTeeParser.ParallelExecStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SpawnExecStmt}
	 * labeled alternative in {@link ProperTeeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSpawnExecStmt(ProperTeeParser.SpawnExecStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code FlowStmt}
	 * labeled alternative in {@link ProperTeeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFlowStmt(ProperTeeParser.FlowStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ExprStmt}
	 * labeled alternative in {@link ProperTeeParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExprStmt(ProperTeeParser.ExprStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#assignment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignment(ProperTeeParser.AssignmentContext ctx);
	/**
	 * Visit a parse tree produced by the {@code VarLValue}
	 * labeled alternative in {@link ProperTeeParser#lvalue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarLValue(ProperTeeParser.VarLValueContext ctx);
	/**
	 * Visit a parse tree produced by the {@code GlobalVarLValue}
	 * labeled alternative in {@link ProperTeeParser#lvalue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGlobalVarLValue(ProperTeeParser.GlobalVarLValueContext ctx);
	/**
	 * Visit a parse tree produced by the {@code PropLValue}
	 * labeled alternative in {@link ProperTeeParser#lvalue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPropLValue(ProperTeeParser.PropLValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#block}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlock(ProperTeeParser.BlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#ifStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfStatement(ProperTeeParser.IfStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#functionDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDef(ProperTeeParser.FunctionDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#parameterList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParameterList(ProperTeeParser.ParameterListContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#parallelStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParallelStmt(ProperTeeParser.ParallelStmtContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#monitorClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMonitorClause(ProperTeeParser.MonitorClauseContext ctx);
	/**
	 * Visit a parse tree produced by the {@code SpawnKeyStmt}
	 * labeled alternative in {@link ProperTeeParser#spawnStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSpawnKeyStmt(ProperTeeParser.SpawnKeyStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ConditionLoop}
	 * labeled alternative in {@link ProperTeeParser#iterationStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionLoop(ProperTeeParser.ConditionLoopContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ValueLoop}
	 * labeled alternative in {@link ProperTeeParser#iterationStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitValueLoop(ProperTeeParser.ValueLoopContext ctx);
	/**
	 * Visit a parse tree produced by the {@code KeyValueLoop}
	 * labeled alternative in {@link ProperTeeParser#iterationStmt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitKeyValueLoop(ProperTeeParser.KeyValueLoopContext ctx);
	/**
	 * Visit a parse tree produced by the {@code BreakStmt}
	 * labeled alternative in {@link ProperTeeParser#flowControl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBreakStmt(ProperTeeParser.BreakStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ContinueStmt}
	 * labeled alternative in {@link ProperTeeParser#flowControl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitContinueStmt(ProperTeeParser.ContinueStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ReturnStmt}
	 * labeled alternative in {@link ProperTeeParser#flowControl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReturnStmt(ProperTeeParser.ReturnStmtContext ctx);
	/**
	 * Visit a parse tree produced by the {@code AndExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAndExpr(ProperTeeParser.AndExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MultiplicativeExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicativeExpr(ProperTeeParser.MultiplicativeExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code AdditiveExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveExpr(ProperTeeParser.AdditiveExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ComparisonExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonExpr(ProperTeeParser.ComparisonExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code NotExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNotExpr(ProperTeeParser.NotExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code AtomExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAtomExpr(ProperTeeParser.AtomExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code MemberAccessExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMemberAccessExpr(ProperTeeParser.MemberAccessExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code OrExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOrExpr(ProperTeeParser.OrExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code UnaryMinusExpr}
	 * labeled alternative in {@link ProperTeeParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryMinusExpr(ProperTeeParser.UnaryMinusExprContext ctx);
	/**
	 * Visit a parse tree produced by the {@code StaticAccess}
	 * labeled alternative in {@link ProperTeeParser#access}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStaticAccess(ProperTeeParser.StaticAccessContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrayAccess}
	 * labeled alternative in {@link ProperTeeParser#access}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayAccess(ProperTeeParser.ArrayAccessContext ctx);
	/**
	 * Visit a parse tree produced by the {@code StringKeyAccess}
	 * labeled alternative in {@link ProperTeeParser#access}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringKeyAccess(ProperTeeParser.StringKeyAccessContext ctx);
	/**
	 * Visit a parse tree produced by the {@code VarEvalAccess}
	 * labeled alternative in {@link ProperTeeParser#access}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarEvalAccess(ProperTeeParser.VarEvalAccessContext ctx);
	/**
	 * Visit a parse tree produced by the {@code EvalAccess}
	 * labeled alternative in {@link ProperTeeParser#access}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEvalAccess(ProperTeeParser.EvalAccessContext ctx);
	/**
	 * Visit a parse tree produced by the {@code FuncAtom}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFuncAtom(ProperTeeParser.FuncAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code GlobalVarReference}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGlobalVarReference(ProperTeeParser.GlobalVarReferenceContext ctx);
	/**
	 * Visit a parse tree produced by the {@code VarReference}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarReference(ProperTeeParser.VarReferenceContext ctx);
	/**
	 * Visit a parse tree produced by the {@code DecimalAtom}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDecimalAtom(ProperTeeParser.DecimalAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code IntegerAtom}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIntegerAtom(ProperTeeParser.IntegerAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code StringAtom}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStringAtom(ProperTeeParser.StringAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code BooleanAtom}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBooleanAtom(ProperTeeParser.BooleanAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ObjectAtom}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectAtom(ProperTeeParser.ObjectAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ArrayAtom}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayAtom(ProperTeeParser.ArrayAtomContext ctx);
	/**
	 * Visit a parse tree produced by the {@code ParenAtom}
	 * labeled alternative in {@link ProperTeeParser#atom}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParenAtom(ProperTeeParser.ParenAtomContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#functionCall}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionCall(ProperTeeParser.FunctionCallContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#objectLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectLiteral(ProperTeeParser.ObjectLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#objectEntry}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectEntry(ProperTeeParser.ObjectEntryContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#objectKey}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitObjectKey(ProperTeeParser.ObjectKeyContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#arrayLiteral}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayLiteral(ProperTeeParser.ArrayLiteralContext ctx);
	/**
	 * Visit a parse tree produced by {@link ProperTeeParser#comparisonOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonOp(ProperTeeParser.ComparisonOpContext ctx);
}