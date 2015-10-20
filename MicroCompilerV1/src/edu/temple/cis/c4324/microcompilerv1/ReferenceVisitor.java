package edu.temple.cis.c4324.microcompilerv1;

import edu.temple.cis.c4324.micro.MicroBaseVisitor;
import edu.temple.cis.c4324.micro.MicroParser;
import edu.temple.cis.c4324.micro.MicroParser.ArithopContext;
import edu.temple.cis.c4324.micro.MicroParser.Assignment_statementContext;
import edu.temple.cis.c4324.micro.MicroParser.BoolContext;
import edu.temple.cis.c4324.micro.MicroParser.CharContext;
import edu.temple.cis.c4324.micro.MicroParser.FloatContext;
import edu.temple.cis.c4324.micro.MicroParser.IdContext;
import edu.temple.cis.c4324.micro.MicroParser.ProgramContext;
import static edu.temple.cis.c4324.microcompilerv1.PrimitiveType.BOOL;
import static edu.temple.cis.c4324.microcompilerv1.PrimitiveType.VOID;
import edu.temple.cis.c4324.micro.MicroParser.If_statementContext;
import edu.temple.cis.c4324.micro.MicroParser.IntContext;
import edu.temple.cis.c4324.micro.MicroParser.LogicalopContext;
import edu.temple.cis.c4324.micro.MicroParser.PowopContext;
import edu.temple.cis.c4324.micro.MicroParser.UnaryopContext;
import static edu.temple.cis.c4324.microcompilerv1.MicroCompilerV1.error;
import static edu.temple.cis.c4324.microcompilerv1.PrimitiveType.CHAR;
import static edu.temple.cis.c4324.microcompilerv1.PrimitiveType.REAL;
import static edu.temple.cis.c4324.microcompilerv1.PrimitiveType.INT;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

public class ReferenceVisitor extends MicroBaseVisitor<Type> {

    private final ParseTreeProperty<Scope> scopeMap;
    private final ParseTreeProperty<Type> typeMap;

    private Scope globalScope;
    private Scope currentScope;

    public ReferenceVisitor(ParseTreeProperty<Scope> scopeMap) {
        this.scopeMap = scopeMap;
        typeMap = new ParseTreeProperty<>();
    }

    public ParseTreeProperty<Type> getTypeMap() {
        return typeMap;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Initialize currentScope and globalScope. Then visit children.
     * </p>
     *
     * @param ctx The ProgramContext node.
     * @return VOID
     */
    @Override
    public Type visitProgram(ProgramContext ctx) {
        currentScope = scopeMap.get(ctx);
        globalScope = currentScope;
        visitChildren(ctx);
        typeMap.put(ctx, VOID);
        return VOID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Determine if the assignment is valid. BOOL and only be assigned to BOOL.
     * INT or REAL can be assigned to INT or REAL.</p>
     *
     * @param ctx The Assignment_Statement Context parse tree node
     * @return VOID
     */
    @Override
    public Type visitAssignment_statement(Assignment_statementContext ctx) {
        Identifier lhs = currentScope.resolve(ctx.ID().getText());
        if (lhs == null) {
            MicroCompilerV1.error(ctx, ctx.ID().getText() + " is not defined");
        } else {
            Type lhsType = lhs.getType();
            Type rhsType = visit(ctx.expr());
            if (!assignmentValid(lhsType, rhsType)) {
                MicroCompilerV1.error(ctx, rhsType + " cannot be assigned to " + lhsType);
            }
        }
        typeMap.put(ctx, VOID);
        return VOID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Determine if the if statement is valid. The guard expression must be of
     * type BOOL.
     * </p>
     *
     * @param ctx The If_statement context parse tree node.
     * @return VOID
     */
    @Override
    public Type visitIf_statement(If_statementContext ctx) {
        Type guardType = visit(ctx.expr());
        if (guardType != BOOL) {
            error(ctx, "If statement guard is not a boolean type");
        }
        ctx.elsif_part().forEach(elsif_part -> visit(elsif_part));
        typeMap.put(ctx, VOID);
        return VOID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Determine if the elsif part is valid. The guard expression must be of
     * type BOOL.
     * </p>
     *
     * @param ctx The elsif_part context parse tree node.
     * @return VOID
     */
    @Override
    public Type visitElsif_part(MicroParser.Elsif_partContext ctx) {
        Type guardType = visit(ctx.expr());
        if (guardType != BOOL) {
            error(ctx, "If statement guard is not a boolean type");
        }
        typeMap.put(ctx, VOID);
        return VOID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Determine if the while_statement part is valid. The guard expression must
     * be of type BOOL.
     * </p>
     *
     * @param ctx The If_statement context parse tree node.
     * @return VOID
     */
    @Override
    public Type visitWhile_statement(MicroParser.While_statementContext ctx) {
        Type guardType = visit(ctx.expr());
        if (guardType != BOOL) {
            error(ctx, "While statement guard is not a boolean type");
        }
        visit(ctx.statement_list());
        typeMap.put(ctx, VOID);
        return VOID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Determine the result type of a POW expression.</p>
     * @param ctx The POW op context parse tree node
     * @return The result type or VOID if invalid.
     */
    @Override
    public Type visitPowop(PowopContext ctx) {
        Type lhsType = visit(ctx.expr(0));
        Type rhsType = visit(ctx.expr(1));
        Type resultType = determineExpressionResult(lhsType, rhsType);
        if (VOID == resultType) {
            error(ctx, lhsType + " cannot be combined with " + rhsType);
            typeMap.put(ctx, VOID);
            return VOID;
        }
        typeMap.put(ctx, resultType);
        return resultType;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Determine the result type of a unary operator.</p>
     * @param ctx The UnaryOpContext parse tree node
     * @return The result type of VOID if not valid.
     */
    @Override
    public Type visitUnaryop(UnaryopContext ctx) {
        String op = ctx.op.getText();
        Type rhsType = visit(ctx.expr());
        if (rhsType instanceof PrimitiveType) {
            switch((PrimitiveType)rhsType) {
                case REAL: 
                    if (op.equals("-")|| op.equals("+")) {
                        typeMap.put(ctx, REAL);
                        return REAL;
                    }
                    error(ctx, op + " cannot be applied to REAL");
                    break;
                case INT:
                    if (op.equals("-") || op.equals("+") || op.equals("~")){
                        typeMap.put(ctx, INT);
                        return INT;
                    }
                    error(ctx, op + " cannot be applied to INT");
                    break;
                case BOOL:
                    if (op.equals("\00ac")){
                        typeMap.put(ctx, BOOL);
                        return BOOL;
                    }
                    error(ctx, op + " cannot be applied to BOOL");
                    break;
            }
        }
        typeMap.put(ctx, VOID);
        return VOID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Determine result type of logical op. Both operands must be BOOL</p>
     * @param ctx LogicalopContext node.
     * @return BOOL if valid, VOID otherwise
     */
    @Override
    public Type visitLogicalop(LogicalopContext ctx) {
        Type lhsType = visit(ctx.expr(0));
        Type rhsType = visit(ctx.expr(1));
        if (BOOL == lhsType && BOOL == rhsType) {
            typeMap.put(ctx, BOOL);
            return BOOL;
        }
        error(ctx, lhsType + " cannot be combined using a logical operator with " + rhsType);
        typeMap.put(ctx, VOID);
        return VOID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Determine the type of an ID</p>
     * @param ctx The parse tree node.
     * @return The type of the identifier.
     */
    @Override
    public Type visitId(IdContext ctx) {
        String idName = ctx.ID().getText();
        Identifier id = currentScope.resolve(idName);
        if (id != null) {
            typeMap.put(ctx,id.getType());
            return id.getType();
        }
        error(ctx, "Undefined identifier " + idName);
        typeMap.put(ctx, VOID);
        return VOID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The type of an Int litteral is INT.</p>
     * @param ctx The parse tree node.
     * @return INT
     */
    @Override
    public Type visitInt(IntContext ctx) {
        typeMap.put(ctx, INT);
        return INT;
    }
    
    @Override
    public Type visitFloat(FloatContext ctx) {
        typeMap.put(ctx, REAL);
        return REAL;
    }
    
    @Override
    public Type visitChar(CharContext ctx) {
        typeMap.put(ctx, CHAR);
        return CHAR;
    }
    
    @Override
    public Type visitBool(BoolContext ctx) {
        typeMap.put(ctx, BOOL);
        return BOOL;
    }
    
    @Override
    public Type visitParens(MicroParser.ParensContext ctx) {
        Type resultType = visit(ctx.expr());
        typeMap.put(ctx, resultType);
        return resultType;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Determine the result type of an arithmetic expression.</p>
     * @param ctx The parse tree node.
     * @return The result type or VOID if invalid.
     */
    @Override
    public Type visitArithop(ArithopContext ctx) {
        Type lhsType = visit(ctx.expr(0));
        Type rhsType = visit(ctx.expr(1));
        Type resultType = determineExpressionResult(lhsType, rhsType);
        if (VOID == resultType) {
            error(ctx, lhsType + " cannot be combined with " + rhsType);
            typeMap.put(ctx, VOID);
            return VOID;
        }
        typeMap.put(ctx, resultType);
        return resultType;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Determine the result of a comparison operation. 
     * REAL and INT can be compared. INT is converted to REAL if 
     * one operand is REAL.</p>
     * @param ctx The parse tree node
     * @return BOOL or VOID if not valid.
     */
    @Override
    public Type visitCompop(MicroParser.CompopContext ctx) {
        Type lhsType = visit(ctx.expr(0));
        Type rhsType = visit(ctx.expr(1));
        Type resultType = determineExpressionResult(lhsType, rhsType);
        if (resultType != VOID) {
            typeMap.put(ctx, BOOL);
            return BOOL;
        }
        typeMap.put(ctx,VOID);
        return VOID;
    }

    /**
     * Determine if an assignment is valid. The valid assignments are:
     * bool to bool, int or real to int, int or real to real.
     * @param lhsType The target type
     * @param rhsType The source type
     * @return true if the assignment is valid.
     */
    private static boolean assignmentValid(Type lhsType, Type rhsType) {
        if (lhsType instanceof PrimitiveType) {
            switch ((PrimitiveType) lhsType) {
                case VOID:
                    return false;
                case BOOL:
                    return rhsType == BOOL;
                case CHAR:
                    return rhsType == CHAR;
                case INT:
                    return (rhsType == INT) || (rhsType == REAL);
                case REAL:
                    return (rhsType == INT) || (rhsType == REAL);
            }
        }
        return false;
    }

    /**
     * Determine the result type of an expression. If both operands are bool, 
     * then the result is bool. If both operands are int, the result is int.
     * If both operands are real, the result is real. If one operand is in 
     * and the other is real, the result is real.
     * @param lhsType The left operand type
     * @param rhsType The right operand type
     * @return The result type, or VOID if the combinations are not valid.
     */
    public static PrimitiveType determineExpressionResult(Type lhsType, Type rhsType) {
        if (lhsType instanceof PrimitiveType) {
            switch ((PrimitiveType) lhsType) {
                case VOID:
                    return VOID;
                case BOOL:
                    if (BOOL == rhsType) {
                        return BOOL;
                    } else {
                        return VOID;
                    }
                case INT:
                    if (INT == rhsType) {
                        return INT;
                    } else if (REAL == rhsType) {
                        return REAL;
                    } else {
                        return VOID;
                    }
                case REAL:
                    if ((REAL == rhsType) || (INT == rhsType)) {
                        return REAL;
                    } else {
                        return VOID;
                    }
            }
        }
        return VOID;
    }

}
