package edu.temple.cis.c4324.microcompilerv1;

import edu.temple.cis.c4324.micro.MicroBaseVisitor;
import edu.temple.cis.c4324.micro.MicroParser.DeclerationContext;
import edu.temple.cis.c4324.micro.MicroParser.PrimitiveTypeContext;
import edu.temple.cis.c4324.micro.MicroParser.ProgramContext;
import org.antlr.v4.runtime.tree.ParseTreeProperty;

/**
 * The definition phase visits the parse tree and defines all of the identifiers.
 * @author Paul
 */
public class DefinitionVisitor extends MicroBaseVisitor<Type> {
    
    private final ParseTreeProperty<Scope> scopeMap;
    private Scope globalScope;
    private Scope currentScope;
    
    public DefinitionVisitor() {
        scopeMap = new ParseTreeProperty<>();
    }
    
    public ParseTreeProperty<Scope> getScopeMap() {return scopeMap;}
    
    @Override
    public Type visitProgram(ProgramContext ctx) {
        String programName = ctx.ID().getText();
        Identifier program = new Identifier(programName, null, null);
        globalScope = new Scope(Scope.Kind.GLOBAL, null, program);
        currentScope = globalScope;
        scopeMap.put(ctx, currentScope);
        visitChildren(ctx);
        return null;
    }
    
    
    @Override
    public Type visitPrimitiveType(PrimitiveTypeContext ctx) {
        String text = ctx.getText();
        switch (text) {
            case "int": return PrimitiveType.INT;
            case "real": return PrimitiveType.REAL;
            case "char": return PrimitiveType.CHAR;
            case "bool": return PrimitiveType.BOOL;
        }
        return null;
    }
       
    @Override
    public Type visitDecleration(DeclerationContext ctx) {
        String idName = ctx.ID().getText();
        Type type = visit(ctx.type());
        if (!currentScope.define(idName, type)) {
            MicroCompilerV1.error(ctx, idName + " is already defined");
        }
        return type;
    }
    
}
