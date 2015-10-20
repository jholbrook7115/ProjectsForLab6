package edu.temple.cis.c4324.microcompilerv1;

import edu.temple.cis.c4324.codegen.CodeGenerator;
import edu.temple.cis.c4324.codegen.InstructionList;
import edu.temple.cis.c4324.micro.MicroBaseVisitor;
import edu.temple.cis.c4324.micro.MicroParser.ArithopContext;
import edu.temple.cis.c4324.micro.MicroParser.Assignment_statementContext;
import edu.temple.cis.c4324.micro.MicroParser.BoolContext;
import edu.temple.cis.c4324.micro.MicroParser.CharContext;
import edu.temple.cis.c4324.micro.MicroParser.CompopContext;
import edu.temple.cis.c4324.micro.MicroParser.Do_until_statementContext;
import edu.temple.cis.c4324.micro.MicroParser.Else_partContext;
import edu.temple.cis.c4324.micro.MicroParser.Elsif_partContext;
import edu.temple.cis.c4324.micro.MicroParser.DeclerationContext;
import edu.temple.cis.c4324.micro.MicroParser.FloatContext;
import edu.temple.cis.c4324.micro.MicroParser.IdContext;
import edu.temple.cis.c4324.micro.MicroParser.If_statementContext;
import edu.temple.cis.c4324.micro.MicroParser.IntContext;
import edu.temple.cis.c4324.micro.MicroParser.LogicalopContext;
import edu.temple.cis.c4324.micro.MicroParser.ParensContext;
import edu.temple.cis.c4324.micro.MicroParser.PowopContext;
import edu.temple.cis.c4324.micro.MicroParser.ProgramContext;
import edu.temple.cis.c4324.micro.MicroParser.Read_statementContext;
import edu.temple.cis.c4324.micro.MicroParser.Statement_listContext;
import edu.temple.cis.c4324.micro.MicroParser.UnaryopContext;
import edu.temple.cis.c4324.micro.MicroParser.While_statementContext;
import edu.temple.cis.c4324.micro.MicroParser.Write_statementContext;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.MethodGen;

public class CompileVisitor extends MicroBaseVisitor<InstructionList> {

    private final CodeGenerator cg;
    private final String sourceFileName;
    private boolean inDefined;

    private final ParseTreeProperty<Scope> scopeMap;
    private final ParseTreeProperty<Type> typeMap;

    private Scope globalScope;
    private Scope currentScope;

    public ParseTreeProperty<Type> getTypeMap() {
        return typeMap;
    }

    /**
     * Construct the CompileVisitor
     *
     * @param scopeMap The scope map created by the Definition Visitor.
     * @param typeMap Tye type map created by the Reference visitor.
     * @param sourceFileName The source file name for error messages
     * @param cg The code generator.
     */
    public CompileVisitor(ParseTreeProperty<Scope> scopeMap,
            ParseTreeProperty<Type> typeMap,
            String sourceFileName, CodeGenerator cg) {
        this.cg = cg;
        this.sourceFileName = sourceFileName;
        inDefined = false;
        this.scopeMap = scopeMap;
        this.typeMap = typeMap;
    }

    @Override
    public InstructionList visitProgram(ProgramContext ctx) {
        currentScope = scopeMap.get(ctx);
        globalScope = currentScope;
        cg.beginClass(sourceFileName, ctx.ID().getText());
        ctx.decleration().forEach(decl -> visitDecleration(decl));
        MethodGen mg = cg.beginMain();
        InstructionList il = cg.newInstructionList();
        il.append(visit(ctx.statement_list()));
        il.addInstruction("return");
        mg.getInstructionList().append(il);
        cg.endMethod();
        return null;
    }

    @Override
    public InstructionList visitDecleration(DeclerationContext ctx) {
        Identifier id = currentScope.resolve(ctx.ID().getText());
        String variableName = id.getName();
        Type variableType = id.getType();
        String variableTypeName = variableType.getJavaTypeName();
        cg.addStaticField(variableName, variableTypeName);
        return null;
    }

    @Override
    public InstructionList visitStatement_list(Statement_listContext ctx) {
        InstructionList il = cg.newInstructionList();
        int numChildren = ctx.getChildCount();
        for (int i = 0; i < numChildren; i++) {
            il.append(visit(ctx.getChild(i)));
        }
        return il;
    }

    @Override
    public InstructionList visitRead_statement(Read_statementContext ctx) {
        InstructionList il = cg.newInstructionList();
        if (!inDefined) {
            cg.addLocalVariable("$in", "java.util.Scanner");
            il.addInstruction("new", "java.util.Scanner");
            il.addInstruction("dup");
            il.addInstruction("getstatic", "java.lang.System.in", "java.io.InputStream");
            il.addInstruction("invokespecial", "java.util.Scanner.<init>", "void", "java.io.InputStream");
            il.addInstruction("astore", "$in");
            inDefined = true;
        }
        ctx.id_list().ID().forEach(idCtx -> {
            Identifier id = currentScope.resolve(idCtx.getText());
            String idTypeName = id.getType().getJavaTypeName();
            String scannerMethodName = "next" + toInitalUc(idTypeName);
            il.addInstruction("aload", "$in");
            il.addInstruction("invokevirtual", "java.util.Scanner." + scannerMethodName, idTypeName);
            if (id.getScope().getKind() == Scope.Kind.GLOBAL) {
                il.addInstruction("putstatic", cg.getClassName() + "." + id.getName(), idTypeName);
            } else {
                MicroCompilerV1.error(idCtx.getSymbol(), "Only global variables currently supported");
            }
        });
        return il;
    }

    @Override
    public InstructionList visitAssignment_statement(Assignment_statementContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.append(visit(ctx.expr()));
        Identifier id = currentScope.resolve(ctx.ID().getText());
        String idTypeName = id.getType().getJavaTypeName();
        if (id.getScope().getKind() == Scope.Kind.GLOBAL) {
            il.addInstruction("putstatic", cg.getClassName() + "." + id.getName(), idTypeName);
        } else {
            MicroCompilerV1.error(ctx.ID().getSymbol(), "Only global variables currently supported");
        }
        return il;
    }


    @Override
    public InstructionList visitUnaryop(UnaryopContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.append(visit(ctx.expr()));
        Type exprType = typeMap.get(ctx.expr());
        String typeName = exprType.getJavaTypeName();
        switch (ctx.op.getText()) {
            case "-":
                switch (typeName) {
                    case "int":
                    case "double":
                        il.addInstruction("neg", typeName);
                        break;
                    default:
                        MicroCompilerV1.error(ctx, "- cannot be applied to " + exprType.toString());
                        break;
                }
                break;
            case "~":
                if (exprType == PrimitiveType.INT) {
                    il.addInstruction("const", "-1");
                    il.addInstruction("op", "^", "int");
                } else {
                    MicroCompilerV1.error(ctx, "~ cannot be applied to " + exprType.toString());
                }
                break;
            case "\u00ac":
                if (exprType == PrimitiveType.BOOL) {
                    il.addInstruction("const", "1");
                    il.addInstruction("op", "^", "int");
                } else {
                    MicroCompilerV1.error(ctx, "~ cannot be applied to " + exprType.toString());
                }
                break;
        }
        return il;
    }
    
    @Override
    public InstructionList visitArithop(ArithopContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.append(visit(ctx.expr(0)));
        il.append(visit(ctx.expr(1)));
        il.addInstruction("op", ctx.op.getText(), "int");
        return il;
    }
    
    @Override
    public InstructionList visitPowop(PowopContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.append(visit(ctx.expr(0)));
        il.addInstruction("cast", "int", "double");
        il.append(visit(ctx.expr(1)));
        il.addInstruction("cast", "int", "double");
        il.addInstruction("invokestatic", "java.lang.Math.pow", "double", "double", "double");
        il.addInstruction("cast", "double", "int");
        return il;
    }
       
    @Override 
    public InstructionList visitCompop(CompopContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.append(visit(ctx.expr(0)));
        il.append(visit(ctx.expr(1)));
        String cmpop = ctx.op.getText();
        if (cmpop.equals("=")) cmpop = "==";
        InstructionList il2 = cg.newInstructionList();
        InstructionHandle load1 = il2.addInstruction("const", "1", "int");
        InstructionHandle end = il2.addInstruction("nop");
        il.createIf(cmpop, "int", load1);
        il.addInstruction("const", "0", "int");
        il.createGoTo(end);
        il.append(il2);
        return il;
    }

    @Override
    public InstructionList visitLogicalop(LogicalopContext ctx) {
        InstructionList il = cg.newInstructionList();
        InstructionList il1 = cg.newInstructionList();
        InstructionHandle theEnd = il1.addInstruction("nop");
        il.append(visit(ctx.expr(0)));
        switch (ctx.op.getText()) {
            case "\u2227":  // Logical and
                il.addInstruction("dup");
                il.createIf("==0", "int", theEnd);
                il.addInstruction("pop");
                break;
            case "\u2228":
                il.addInstruction("dup");
                il.createIf("!=0", "int", theEnd);
                il.addInstruction("pop");
                break;
        }
        il.append(visit(ctx.expr(1)));
        il.append(il1);
        return il;
    }

    @Override
    public InstructionList visitId(IdContext ctx) {
        InstructionList il = cg.newInstructionList();
        Identifier id = currentScope.resolve(ctx.getText());
        String javaTypeName = id.getType().getJavaTypeName();
        if (id.getScope().getKind() == Scope.Kind.GLOBAL) {
            il.addInstruction("getstatic", cg.getClassName() + "." + id.getName(), javaTypeName);
        } else {
            MicroCompilerV1.error(ctx, "Only global variables currently supported");
        }
        return il;
    }
    
    @Override
    public InstructionList visitInt(IntContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.addInstruction("const", ctx.getText(), "int");
        return il;
    }

    @Override
    public InstructionList visitFloat(FloatContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.addInstruction("const", ctx.getText(), "double");
        return il;
    }
    
    @Override
    public InstructionList visitChar(CharContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.addInstruction("const", ctx.getText(), "char");
        return il;
    }
    
    @Override
    public InstructionList visitBool(BoolContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.addInstruction("const", ctx.getText(), "boolean");
        return il;
    }
    
    @Override
    public InstructionList visitParens(ParensContext ctx) {
        return visit(ctx.expr());
    }
    
    @Override
    public InstructionList visitWrite_statement(Write_statementContext ctx) {
        InstructionList il = cg.newInstructionList();
        ctx.expr_list().expr().forEach(expr -> {
            il.addInstruction("getstatic", "java.lang.System.out", "java.io.PrintStream");
            il.append(visit(expr));
            Type exprType = typeMap.get(expr);
            il.addInstruction("invokevirtual", "java.io.PrintStream.println", "void", exprType.getJavaTypeName());
        });
        return il;
    }

    @Override
    public InstructionList visitWhile_statement(While_statementContext ctx) {
        InstructionList il = cg.newInstructionList();
        InstructionHandle topOfLoop = il.addInstruction("nop");
        InstructionHandle endOfLoop = il.createGoTo(topOfLoop);
        InstructionHandle outOfLoop = il.addInstruction("nop");
        InstructionList ifStatement = cg.newInstructionList();
        ifStatement.createIf("==0", "int", outOfLoop);
        il.append(topOfLoop, ifStatement);
        il.append(topOfLoop, visit(ctx.expr()));
        il.insert(endOfLoop, visit(ctx.statement_list()));
        return il;
    }

    @Override
    public InstructionList visitDo_until_statement(Do_until_statementContext ctx) {
        InstructionList il = cg.newInstructionList();
        InstructionHandle topOfLoop = il.addInstruction("nop");
        il.append(visit(ctx.statement_list()));
        il.append(visit(ctx.expr()));
        il.createIf("==0", "int", topOfLoop);
        return il;
    }
        
    @Override
    public InstructionList visitIf_statement(If_statementContext ctx) {
        InstructionList il = cg.newInstructionList();
        il.append(visit(ctx.expr()));
        InstructionList il1 = cg.newInstructionList();
        InstructionHandle theEnd = il1.addInstruction("nop");
        InstructionList il2 = cg.newInstructionList();
        InstructionHandle falseTarget = il2.addInstruction("nop");
        il.createIf("==0", "int", falseTarget);
        il.append(visit(ctx.statement_list()));
        List<Elsif_partContext> elsifPart = ctx.elsif_part();
        Else_partContext elsePart = ctx.else_part();
        if (ctx.else_part() != null || (ctx.elsif_part() != null && !ctx.elsif_part().isEmpty())) {
            il.createGoTo(theEnd);
        }
        il.append(il2);
        if (ctx.elsif_part() != null && !ctx.elsif_part().isEmpty()) {
            ctx.elsif_part().forEach(elif -> {
                InstructionList il3 = cg.newInstructionList();
                InstructionHandle falseTarget2 = il3.addInstruction("nop");
                il.append(visit(elif.expr()));
                il.createIf("==0", "int", falseTarget2);
                il.append(visit(elif.statement_list()));
                il.createGoTo(theEnd);
                il.append(il3);
            });
        }
        if (ctx.else_part() != null) {
            il.append(visit(ctx.else_part().statement_list()));
        }
        if (ctx.else_part() != null || (ctx.elsif_part() != null && !ctx.elsif_part().isEmpty())) {
            il.append(il1);
        } else {
            il1.dispose();
        }
        return il;
    }

    public String toInitalUc(String s) {
        char[] charArray = s.toCharArray();
        charArray[0] = Character.toUpperCase(charArray[0]);
        return new String(charArray);
    }

}