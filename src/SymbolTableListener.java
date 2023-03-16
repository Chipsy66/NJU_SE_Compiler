import java.util.*;
import java.util.concurrent.TimeoutException;

public class SymbolTableListener extends SysYParserBaseListener {
    private final SymbolTableTreeGraph graph = new SymbolTableTreeGraph();

    private GlobalScope globalScope = null;
    private Scope currentScope = null;
    private int localScopeCounter = 0;

    public Set<Integer> opSet = new HashSet<>();

    public SymbolTableTreeGraph getGraph() {
        return graph;
    }

    @Override
    public void enterProgram(SysYParserParser.ProgramContext ctx) { // enter a new scope
        globalScope = new GlobalScope(null);
        currentScope = globalScope;
    }

    @Override
    public void enterFuncDef(SysYParserParser.FuncDefContext ctx) {// enter a new scope, and define a symbol
        String funcName = ctx.IDENT().getText();
        FunctionSymbolScope functionSymbolScope = new FunctionSymbolScope(funcName, currentScope);

        graph.addEdge(funcName, currentScope.getName());

        if (!currentScope.define(functionSymbolScope)) {
            // 函数重复定义
            opSet.add(ctx.start.getLine());
            ErrorPrinter.print(4, ctx.start.getLine());
            functionSymbolScope.isFalse = true;
        }


        currentScope = functionSymbolScope;
        String typeName = ctx.funcType().getText();
        functionSymbolScope.returnType = (Type) globalScope.resolve(typeName);
        if (ctx.funcFParams() != null) {
            for (SysYParserParser.FuncFParamContext funcFParamContext : ctx.funcFParams().funcFParam()) {
                if (inValidFunc(currentScope))
                    return;

                Type type = null;
                if (funcFParamContext.bType().getText().equals("int"))
                    type = new BasicTypeSymbol("int");

                if (funcFParamContext.L_BRACKT().size() > 0)
                    type = new ArrayType(funcFParamContext.L_BRACKT().size());

                String varName = funcFParamContext.IDENT().getText();
                VariableSymbol varSymbol = new VariableSymbol(varName, type);

                if (!currentScope.define(varSymbol)) {   // 变量重复定义
                    opSet.add(ctx.start.getLine());
                    //System.err.println("Error type 3 at Line "+ctx.start.getLine());
                    ErrorPrinter.print(3, ctx.start.getLine());
                } else {
                    functionSymbolScope.paramsTypes.add(type);
                }

                Symbol symbol = currentScope.getSymbol(funcFParamContext.IDENT().getText());
                if (symbol != null) {
                    symbol.addUse(funcFParamContext.start.getLine(), funcFParamContext.start.getCharPositionInLine() + 4);
                    if (ctx.start.getLine() == Main.lineNo
                            && funcFParamContext.start.getCharPositionInLine() + 4 == Main.column)
                        Main.hit = symbol.getUse();
                }

            }
        }

    }

    @Override
    public void enterBlock(SysYParserParser.BlockContext ctx) {// enter a new scope
        LocalScope localScope = new LocalScope(currentScope);
        String localScopeName = localScope.getName() + localScopeCounter;
        localScope.setName(localScopeName);
        localScopeCounter++;

        graph.addEdge(localScopeName, currentScope.getName());

        currentScope = localScope;
    }

    @Override
    public void exitProgram(SysYParserParser.ProgramContext ctx) { // exit a scope
        currentScope = currentScope.getEnclosingScope();
    }

    @Override
    public void exitFuncDef(SysYParserParser.FuncDefContext ctx) { // exit a scope
        currentScope = currentScope.getEnclosingScope();
    }

    @Override
    public void exitBlock(SysYParserParser.BlockContext ctx) { // exit a scope
        currentScope = currentScope.getEnclosingScope();
    }

    @Override
    public void exitVarDecl(SysYParserParser.VarDeclContext ctx) { // define symbols
        if (inValidFunc(currentScope.getEnclosingScope()))
            return;
        String typeName = ctx.bType().getText();
        Type type = (Type) globalScope.resolve(typeName);


        for (SysYParserParser.VarDefContext varDefContext : ctx.varDef()) {
            if (varDefContext instanceof SysYParserParser.VARDEF_INITContext) {
                SysYParserParser.VARDEF_INITContext initContext = (SysYParserParser.VARDEF_INITContext) varDefContext;

                String left_type_name = "";
                if (initContext.L_BRACKT().size() != 0) {
                    type = new ArrayType(initContext.L_BRACKT().size());
                    left_type_name = String.valueOf(initContext.L_BRACKT().size());
                } else {
                    type = new BasicTypeSymbol("int");
                    left_type_name = "int";
                }

                SysYParserParser.InitValContext initValContext = initContext.initVal();
                if (initValContext.exp() instanceof SysYParserParser.EXP_LVALContext) {
                    SysYParserParser.LValContext right_lValContext = ((SysYParserParser.EXP_LVALContext) initValContext.exp()).lVal();
                    String right_name = right_lValContext.IDENT().getText();
                    Symbol right_symbol = currentScope.getSymbol(right_name);
                    String right_type_name = "";
                    if (right_symbol != null) {
                        if (right_symbol instanceof FunctionSymbolScope
                                && ((FunctionSymbolScope) right_symbol).returnType instanceof BasicTypeSymbol) {
                            right_type_name = ((BasicTypeSymbol) ((FunctionSymbolScope) right_symbol).returnType).name;
                        } else if (right_symbol.getType() instanceof BasicTypeSymbol) {
                            right_type_name = "int";
                        } else if (right_symbol.getType() instanceof ArrayType) {
                            right_type_name = String.valueOf(((ArrayType) right_symbol.getType()).layer);
                        }
                        int right_act = ((SysYParserParser.EXP_LVALContext) initValContext.exp()).lVal().L_BRACKT().size();

                        if (right_type_name.equals(left_type_name)) {

                        } else if (isNumeric(left_type_name)
                                && isNumeric(right_type_name)
                                && Integer.parseInt(left_type_name) == Integer.parseInt(right_type_name) - right_act) {

                        } else if (left_type_name.equals("int")
                                && isNumeric(right_type_name)
                                && Integer.parseInt(right_type_name) == right_act) {

                        } else {
                            // = liang bian bufu
                            opSet.add(ctx.start.getLine());
                            //System.err.println("Error type 5 at Line "+ctx.start.getLine());
                            ErrorPrinter.print(5, ctx.start.getLine());
                        }
                    }


                }
                String varName = ((SysYParserParser.VARDEF_INITContext) varDefContext).IDENT().getText();
                VariableSymbol varSymbol = new VariableSymbol(varName, type);
                if (currentScope.getEnclosingScope() instanceof FunctionSymbolScope) {
                    if (currentScope.getEnclosingScope().currHasSymbol(varName)) {
                        //System.err.println("Error type 3 at Line "+ctx.start.getLine());
                        ErrorPrinter.print(3, ctx.start.getLine());
                        return;
                    }
                }
                if (!currentScope.define(varSymbol)) {   // 变量重复定义
                    opSet.add(ctx.start.getLine());
                    //System.err.println("Error type 3 at Line "+ctx.start.getLine());
                    ErrorPrinter.print(3, ctx.start.getLine());
                }

                Symbol symbol = currentScope.getSymbol(varName);
                symbol.addUse(ctx.start.getLine(), varDefContext.start.getCharPositionInLine());
                if (ctx.start.getLine() == Main.lineNo
                        && varDefContext.start.getCharPositionInLine() == Main.column)
                    Main.hit = symbol.getUse();

            } else if (varDefContext instanceof SysYParserParser.VARDEF_NO_INITContext) {
                SysYParserParser.VARDEF_NO_INITContext initContext = (SysYParserParser.VARDEF_NO_INITContext) varDefContext;

                if (initContext.L_BRACKT().size() != 0)
                    type = new ArrayType(initContext.L_BRACKT().size());
                else
                    type = new BasicTypeSymbol("int");
                String varName = initContext.IDENT().getText();
                VariableSymbol varSymbol = new VariableSymbol(varName, type);
                if (currentScope.getEnclosingScope() instanceof FunctionSymbolScope) {
                    if (currentScope.getEnclosingScope().currHasSymbol(varName)) {
                        //System.err.println("Error type 3 at Line "+ctx.start.getLine());
                        ErrorPrinter.print(3, ctx.start.getLine());
                        return;
                    }
                }
                if (!currentScope.define(varSymbol)) {   // 变量重复定义
                    opSet.add(ctx.start.getLine());
                    //System.err.println("Error type 3 at Line "+ctx.start.getLine());
                    ErrorPrinter.print(3, ctx.start.getLine());
                }

                Symbol symbol = currentScope.getSymbol(varName);
                symbol.addUse(ctx.start.getLine(), varDefContext.start.getCharPositionInLine());
                if (ctx.start.getLine() == Main.lineNo
                        && varDefContext.start.getCharPositionInLine() == Main.column)
                    Main.hit = symbol.getUse();

            }

        }
    }

    @Override
    public void exitConstDecl(SysYParserParser.ConstDeclContext ctx) { // define symbols
        if (inValidFunc(currentScope.getEnclosingScope()))
            return;

        String typeName = ctx.bType().getText();
        Type type = (Type) globalScope.resolve(typeName);

        for (SysYParserParser.ConstDefContext varDefContext : ctx.constDef()) {

            if (varDefContext.L_BRACKT().size() != 0)
                type = new ArrayType(varDefContext.L_BRACKT().size());
            else
                type = new BasicTypeSymbol("int");
            String varName = varDefContext.IDENT().getText();
            VariableSymbol varSymbol = new VariableSymbol(varName, type);
            if (currentScope.getEnclosingScope() instanceof FunctionSymbolScope) {
                if (currentScope.getEnclosingScope().currHasSymbol(varName)) {
                    //System.err.println("Error type 3 at Line "+ctx.start.getLine());
                    ErrorPrinter.print(3, ctx.start.getLine());
                    return;
                }
            }
            if (!currentScope.define(varSymbol)) {   // 变量重复定义
                opSet.add(ctx.start.getLine());
                //System.err.println("Error type 3 at Line "+ctx.start.getLine());
                ErrorPrinter.print(3, ctx.start.getLine());
            }
            Symbol symbol = currentScope.getSymbol(varName);
            symbol.addUse(ctx.start.getLine(), varDefContext.start.getCharPositionInLine());
            if (ctx.start.getLine() == Main.lineNo
                    && varDefContext.start.getCharPositionInLine() == Main.column)
                Main.hit = symbol.getUse();

        }

    }

    @Override
    public void enterEXP_LVAL(SysYParserParser.EXP_LVALContext ctx) {
        if (inValidFunc(currentScope))
            return;
        Symbol symbol = currentScope.getSymbol(ctx.lVal().IDENT().getText());
        if (symbol != null) {
            symbol.addUse(ctx.lVal().start.getLine(), ctx.lVal().start.getCharPositionInLine());
            if (ctx.lVal().start.getLine() == Main.lineNo
                    && ctx.lVal().start.getCharPositionInLine() == Main.column)
                Main.hit = symbol.getUse();
        }
    }

    @Override
    public void exitEXP_FUNC_ARGUMENT(SysYParserParser.EXP_FUNC_ARGUMENTContext ctx) {
        if (inValidFunc(currentScope.getEnclosingScope()))
            return;

        String id = ctx.IDENT().getText();
        Type id_type = currentScope.getType(id);
        if (id_type != null && !(id_type instanceof FunctionSymbolScope)) {   // 对变量使用函数运算符
            opSet.add(ctx.start.getLine());
            //System.err.println("Error type 10 at Line "+ctx.start.getLine()+":"+ctx.getText());
            ErrorPrinter.print(10, ctx.start.getLine());
        }

        String funcName = ctx.IDENT().getText();

        Symbol symbol = currentScope.getSymbol(ctx.IDENT().getText());
        if (symbol != null) {
            symbol.addUse(ctx.start.getLine(), ctx.start.getCharPositionInLine());
            if (ctx.start.getLine() == Main.lineNo && ctx.start.getCharPositionInLine() == Main.column)
                Main.hit = symbol.getUse();
        }


        if (!currentScope.checkSymbol(funcName)) {   // use undefined function
            opSet.add(ctx.start.getLine());
            //System.err.println("Error type 2 at Line "+ctx.start.getLine()+":"+ctx.getText());
            ErrorPrinter.print(2, ctx.start.getLine());

        }

        if (ctx.funcRParams() == null) {
            Symbol thisFunc = currentScope.getSymbol(id);
            if (thisFunc instanceof FunctionSymbolScope && ((FunctionSymbolScope) thisFunc).paramsTypes.size() > 0) {
                {
                    // 函数参数不适用：函数参数的数量或类型与函数声明的参数数量或类型不一致
                    opSet.add(ctx.start.getLine());
                    //System.err.println("Error type 8 at Line " + ctx.start.getLine());
                    ErrorPrinter.print(8, ctx.start.getLine());
                    return;
                }
            }
        }

        if (ctx.funcRParams() != null) {
            Symbol thisFunc = currentScope.getSymbol(id);
            if (thisFunc instanceof FunctionSymbolScope) {
                if (((FunctionSymbolScope) thisFunc).paramsTypes.size() != ctx.funcRParams().param().size()) {
                    // 函数参数不适用：函数参数的数量或类型与函数声明的参数数量或类型不一致
                    opSet.add(ctx.start.getLine());
                    //System.err.println("Error type 8 at Line "+ctx.start.getLine()+":"+ctx.funcRParams().param());
                    ErrorPrinter.print(8, ctx.start.getLine());
                    return;
                }
            }
            for (int i = 0; i < ctx.funcRParams().param().size(); i++) {
                SysYParserParser.ParamContext paramContext = ctx.funcRParams().param().get(i);
                String name = paramContext.getText();


                if (!currentScope.checkSymbol(name)) {   // use undefined var
                    ////System.err.println("Error type 1 at Line "+ctx.start.getLine()+":"+ctx.getText());
                } else if (thisFunc instanceof FunctionSymbolScope) {
                    Type param_type = ((FunctionSymbolScope) thisFunc).paramsTypes.get(i);

                    Type arg_type = currentScope.getType(name);
                    Symbol arg_symbol = currentScope.getSymbol(name);

                    boolean flag = true;
                    if (arg_type instanceof BasicTypeSymbol && param_type instanceof BasicTypeSymbol) {
                        if (Objects.equals(((BasicTypeSymbol) arg_type).name, ((BasicTypeSymbol) param_type).name))
                            flag = false;
                    }
                    if (arg_type instanceof ArrayType && param_type instanceof ArrayType) {
                        if (((ArrayType) arg_type).layer == ((ArrayType) param_type).layer)
                            flag = false;
                    }
                    if (arg_symbol instanceof FunctionSymbolScope && param_type instanceof BasicTypeSymbol) {
                        if (((FunctionSymbolScope) arg_symbol).returnType instanceof BasicTypeSymbol && name.contains("(")) {
                            if (((BasicTypeSymbol) ((FunctionSymbolScope) arg_symbol).returnType).name.equals("int") && ((BasicTypeSymbol) param_type).name.equals("int"))
                                flag = false;
                        }

                    }
                    if (flag) {   // 函数参数不适用：函数参数的数量或类型与函数声明的参数数量或类型不一致
                        opSet.add(ctx.start.getLine());
                        //System.err.println("Error type 8 at Line "+ctx.start.getLine()+"::"+ctx.funcRParams().param().get(i).exp().getText());
                        ErrorPrinter.print(8, ctx.start.getLine());
                    }


                }
            }

        }

    }

    @Override
    public void exitSTMT_ASSIGN(SysYParserParser.STMT_ASSIGNContext ctx) {
        if (inValidFunc(currentScope.getEnclosingScope()))
            return;

        if (currentScope.checkSymbol(ctx.lVal().getText())) {
            String lval_name = ctx.lVal().getText();
            Symbol lval_symbol = currentScope.getSymbol(lval_name);
            if (lval_symbol instanceof FunctionSymbolScope) {   // assign to function
                opSet.add(ctx.start.getLine());
                //System.err.println("Error type 11 at Line "+ctx.start.getLine());
                ErrorPrinter.print(11, ctx.start.getLine());
                return;
            }
        }

        String left_name = ctx.lVal().IDENT().getText();
        Symbol left_symbol = currentScope.getSymbol(left_name);
        if (left_symbol != null) {
            left_symbol.addUse(ctx.lVal().start.getLine(), ctx.lVal().start.getCharPositionInLine());

            if (ctx.lVal().start.getLine() == Main.lineNo
                    && ctx.lVal().start.getCharPositionInLine() == Main.column)
                Main.hit = left_symbol.getUse();
        }
        if (left_symbol == null)
            return;

        Type left_type = left_symbol.getType();
        String left_type_name = "";
        int left_act = ctx.lVal().L_BRACKT().size();
        if (left_type instanceof BasicTypeSymbol) {
            left_type_name = ((BasicTypeSymbol) left_type).name;
        } else if (left_type instanceof ArrayType) {
            left_type_name = String.valueOf(((ArrayType) left_type).layer);
        }

        String right_name = "";
        String right_type_name = "";
        int right_act = 0;
        if (ctx.exp() instanceof SysYParserParser.EXP_LVALContext) {
            right_name = ((SysYParserParser.EXP_LVALContext) ctx.exp()).lVal().IDENT().getText();
            Symbol right_symbol = currentScope.getSymbol(right_name);
            right_act = ((SysYParserParser.EXP_LVALContext) ctx.exp()).lVal().L_BRACKT().size();
            if (right_symbol != null) {
                Type right_symbolType = right_symbol.getType();
                if (right_symbolType instanceof BasicTypeSymbol) {
                    right_type_name = ((BasicTypeSymbol) right_symbolType).name;
                } else if (right_symbolType instanceof ArrayType) {
                    right_type_name = String.valueOf(((ArrayType) right_symbolType).layer);
                }
            }
        } else if (ctx.exp() instanceof SysYParserParser.EXP_FUNC_ARGUMENTContext) {
            right_name = ((SysYParserParser.EXP_FUNC_ARGUMENTContext) ctx.exp()).IDENT().getText();
            Symbol right_symbol = currentScope.getSymbol(right_name);
            if (right_symbol instanceof FunctionSymbolScope) {
                Type returnType = ((FunctionSymbolScope) right_symbol).returnType;
                if (returnType instanceof BasicTypeSymbol) {
                    right_type_name = ((BasicTypeSymbol) returnType).name;
                    if (right_type_name.equals("void")) {
                        opSet.add(ctx.start.getLine());
                        ErrorPrinter.print(5, ctx.start.getLine());
                        return;
                    }
                }
            }
        } else {
            return;
        }
        if (right_act > 0 && !isNumeric(right_type_name))
            return;
        if (right_act > 0 && right_type_name.equals("int"))
            return;
        if (left_act > 0 && left_type_name.equals("int"))
            return;

        if (left_type_name.equals("int")) {
            if (right_type_name.equals("int")) {
            } else if (isNumeric(right_type_name) && Integer.parseInt(right_type_name) == right_act) {
            } else {
                opSet.add(ctx.start.getLine());
                ErrorPrinter.print(5, ctx.start.getLine());
            }
        } else if (isNumeric(left_type_name)) {
            int left_delta = Integer.parseInt(left_type_name) - left_act;
            if (left_delta == 0) {
                if (right_type_name.equals("int")) {
                } else if (isNumeric(right_type_name) && Integer.parseInt(right_type_name) == right_act) {
                } else {
                    opSet.add(ctx.start.getLine());
                    ErrorPrinter.print(5, ctx.start.getLine());
                }
            } else if (left_delta > 0) {
                if (isNumeric(right_type_name) && Integer.parseInt(right_type_name) - right_act == left_delta) {
                } else {
                    opSet.add(ctx.start.getLine());
                    ErrorPrinter.print(5, ctx.start.getLine());
                }
            }
        }


    }

    @Override
    public void enterLVal(SysYParserParser.LValContext ctx) {
        if (inValidFunc(currentScope.getEnclosingScope()))
            return;
        String name = ctx.IDENT().getText();
        Symbol id_symbol = currentScope.getSymbol(name);

        if (id_symbol == null) {   // use undefined var
            opSet.add(ctx.start.getLine());
            //System.err.println("Error type 1 at Line "+ctx.stop.getLine()+"::"+ctx.getText());
            ErrorPrinter.print(1, ctx.start.getLine());
            return;
        }

        if (id_symbol instanceof FunctionSymbolScope
                || id_symbol.getType() instanceof BasicTypeSymbol) {
            if (ctx.L_BRACKT().size() > 0) {   // use [] for var which is not arrayType 对非数组使用下标运算符：对int型变量或函数使用下标运算符
                opSet.add(ctx.start.getLine());
                //System.err.println("Error type 9 at Line "+ctx.start.getLine());
                ErrorPrinter.print(9, ctx.start.getLine());
            }
        } else if (id_symbol.getType() instanceof ArrayType) {
            int layer = ((ArrayType) currentScope.getType(name)).layer;
            if (layer < ctx.L_BRACKT().size()) {   // use [] for var which is not arrayType 对非数组使用下标运算符：对int型变量或函数使用下标运算符
                opSet.add(ctx.start.getLine());
                //System.err.println("Error type 9 at Line "+ctx.start.getLine());
                ErrorPrinter.print(9, ctx.start.getLine());
            }
        }
    }

    @Override
    public void exitSTMT_RETURN(SysYParserParser.STMT_RETURNContext ctx) {
        Scope scope = currentScope;
        Type returnType;
        while (currentScope != null && !(currentScope instanceof FunctionSymbolScope)) {
            currentScope = currentScope.getEnclosingScope();
        }
        boolean isInvalid = inValidFunc(currentScope);

        assert currentScope != null;

        returnType = ((FunctionSymbolScope) currentScope).returnType;
        currentScope = scope;
        if (isInvalid)
            return;
        if (returnType != null) {
            if (returnType instanceof BasicTypeSymbol) {
                String return_type_name = ((BasicTypeSymbol) returnType).name; // int/void
                if (return_type_name.equals("void") && ctx.exp() != null) {   // invalid returnType
                    opSet.add(ctx.start.getLine());
                    //System.err.println("Error type 7 at Line "+ctx.start.getLine()+"::"+ctx.getText());
                    ErrorPrinter.print(7, ctx.start.getLine());
                } else if (return_type_name.equals("int") && ctx.exp() == null) { // invalid returnType
                    opSet.add(ctx.start.getLine());
                    //System.err.println("Error type 7 at Line "+ctx.start.getLine()+"::"+ctx.getText());
                    ErrorPrinter.print(7, ctx.start.getLine());
                } else if (return_type_name.equals("int")) {
                    if (ctx.exp() instanceof SysYParserParser.EXP_LVALContext) {
                        String name = ((SysYParserParser.EXP_LVALContext) ctx.exp()).lVal().IDENT().getText();
                        Type id_type = currentScope.getType(name);
                        if (!(id_type instanceof BasicTypeSymbol)) {
                            if (id_type instanceof ArrayType) {
                                if (((ArrayType) id_type).layer == ((SysYParserParser.EXP_LVALContext) ctx.exp()).lVal().L_BRACKT().size()) {
                                    return;
                                }
                            }
                            // invalid returnType
                            opSet.add(ctx.start.getLine());
                            //System.err.println("Error type 7 at Line "+ctx.start.getLine()+"::"+ctx.getText());
                            ErrorPrinter.print(7, ctx.start.getLine());
                        }
                    } else if (ctx.exp() instanceof SysYParserParser.EXP_FUNC_ARGUMENTContext) {
                        String funcName = ((SysYParserParser.EXP_FUNC_ARGUMENTContext) ctx.exp()).IDENT().getText();
                        Symbol symbol = currentScope.getSymbol(funcName);
                        if ((symbol instanceof FunctionSymbolScope
                                && ((FunctionSymbolScope) symbol).returnType instanceof BasicTypeSymbol
                                && !((BasicTypeSymbol) ((FunctionSymbolScope) symbol).returnType).name.equals("int"))) {
                            opSet.add(ctx.start.getLine());
                            //System.err.println("Error type 7 at Line "+ctx.start.getLine()+"::::"+ctx.getText());
                            ErrorPrinter.print(7, ctx.start.getLine());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void exitUNARY_EXP(SysYParserParser.UNARY_EXPContext ctx) {
        deal_exp_instanceof_lval(ctx.exp(), ctx.start.getLine());
    }

    public void deal_exp_instanceof_lval(SysYParserParser.ExpContext context, int line) {
        if (inValidFunc(currentScope.getEnclosingScope()))
            return;

        if (context instanceof SysYParserParser.EXP_LVALContext) {
            SysYParserParser.EXP_LVALContext exp_lvalContext = (SysYParserParser.EXP_LVALContext) (context);
            SysYParserParser.LValContext lValContext = exp_lvalContext.lVal();
            if (lValContext.L_BRACKT().size() == 0) {
                Symbol id_symbol = currentScope.getSymbol(lValContext.IDENT().getText());

                if (id_symbol == null)
                    return;
                if (id_symbol.getType() instanceof BasicTypeSymbol
                        && !((BasicTypeSymbol) id_symbol.getType()).name.equals("int")) {
                    // invalid op
                    if (!opSet.contains(line)) {
                        opSet.add(line);
                        //System.err.println("Error type 6 at Line "+line);
                        ErrorPrinter.print(6, line);
                    }

                } else if (id_symbol instanceof FunctionSymbolScope) {
                    if (((FunctionSymbolScope) id_symbol).returnType instanceof BasicTypeSymbol
                            && !((BasicTypeSymbol) ((FunctionSymbolScope) id_symbol).returnType).name.equals("int")) {
                        // invalid op
                        if (!opSet.contains(line)) {
                            opSet.add(line);
                            //System.err.println("Error type 6 at Line "+line);
                            ErrorPrinter.print(6, line);
                        }
                    } else if (!lValContext.getText().contains("(")) {
                        // invalid op
                        if (!opSet.contains(line)) {
                            opSet.add(line);
                            //System.err.println("Error type 6 at Line "+line);
                            ErrorPrinter.print(6, line);
                        }
                    }
                } else if (id_symbol.getType() instanceof ArrayType) {
                    // invalid op
                    if (!opSet.contains(line)) {
                        opSet.add(line);
                        //System.err.println("Error type 6 at Line "+line);
                        ErrorPrinter.print(6, line);
                    }
                }
            } else {
                Symbol id_symbol = currentScope.getSymbol(lValContext.IDENT().getText());
                if (id_symbol == null)
                    return;
                if (id_symbol.getType() instanceof ArrayType) {
                    if (((ArrayType) id_symbol.getType()).layer == lValContext.L_BRACKT().size()) {

                    } else if (((ArrayType) id_symbol.getType()).layer < lValContext.L_BRACKT().size()) {
                        opSet.add(line);
                        return;
                    } else {
                        // invalid op
                        if (!opSet.contains(line)) {
                            opSet.add(line);
                            //System.err.println("Error type 6 at Line "+line);
                            ErrorPrinter.print(6, line);
                        }
                    }
                } else {
                    // invalid op
                    if (!opSet.contains(line)) {
                        opSet.add(line);
                        //System.err.println("Error type 6 at Line "+line);
                        ErrorPrinter.print(6, line);
                    }
                }
            }
        } else if (context instanceof SysYParserParser.EXP_FUNC_ARGUMENTContext) {
            String name = ((SysYParserParser.EXP_FUNC_ARGUMENTContext) context).IDENT().getText();
            Symbol id_symbol = currentScope.getSymbol(name);
            if (id_symbol instanceof FunctionSymbolScope) {
                Type returnType = ((FunctionSymbolScope) id_symbol).returnType;
                if (returnType instanceof BasicTypeSymbol) {
                    if (((BasicTypeSymbol) returnType).name.equals("void") && !opSet.contains(line)) {
                        opSet.add(line);
                        //System.err.println("Error type 6 at Line "+line);
                        ErrorPrinter.print(6, line);
                    }
                }
            }
        } else if (context instanceof SysYParserParser.L_EXP_RContext) {
            deal_exp_instanceof_lval(((SysYParserParser.L_EXP_RContext) context).exp(), line);
        }
    }

    @Override
    public void exitEXP_MUL_EXP(SysYParserParser.EXP_MUL_EXPContext ctx) {
        for (int i = 0; i < ctx.exp().size(); i++) {
            SysYParserParser.ExpContext expContext = ctx.exp(i);
            deal_exp_instanceof_lval(expContext, ctx.start.getLine());
        }
    }

    @Override
    public void exitEXP_PLUS_EXP(SysYParserParser.EXP_PLUS_EXPContext ctx) {
        for (int i = 0; i < ctx.exp().size(); i++) {
            SysYParserParser.ExpContext expContext = ctx.exp(i);
            deal_exp_instanceof_lval(expContext, ctx.start.getLine());
        }
    }

    @Override
    public void exitCOND_AND(SysYParserParser.COND_ANDContext ctx) {
        for (int i = 0; i < ctx.cond().size(); i++) {
            SysYParserParser.CondContext expContext = ctx.cond(i);
            if (expContext instanceof SysYParserParser.COND_EXPContext) {
                SysYParserParser.ExpContext exp = ((SysYParserParser.COND_EXPContext) expContext).exp();
                deal_exp_instanceof_lval(exp, ctx.start.getLine());
            }
        }
    }

    @Override
    public void exitCOND_EQ(SysYParserParser.COND_EQContext ctx) {
        for (int i = 0; i < ctx.cond().size(); i++) {
            SysYParserParser.CondContext expContext = ctx.cond(i);
            if (expContext instanceof SysYParserParser.COND_EXPContext) {
                SysYParserParser.ExpContext exp = ((SysYParserParser.COND_EXPContext) expContext).exp();
                deal_exp_instanceof_lval(exp, ctx.start.getLine());
            }
        }
    }

    @Override
    public void exitCOND_LT(SysYParserParser.COND_LTContext ctx) {
        for (int i = 0; i < ctx.cond().size(); i++) {
            SysYParserParser.CondContext expContext = ctx.cond(i);
            if (expContext instanceof SysYParserParser.COND_EXPContext) {
                SysYParserParser.ExpContext exp = ((SysYParserParser.COND_EXPContext) expContext).exp();
                deal_exp_instanceof_lval(exp, ctx.start.getLine());
            }
        }
    }

    @Override
    public void exitCOND_EXP(SysYParserParser.COND_EXPContext ctx) {
        deal_exp_instanceof_lval(ctx.exp(), ctx.start.getLine());
    }

    @Override
    public void exitCOND_OR(SysYParserParser.COND_ORContext ctx) {

        for (int i = 0; i < ctx.cond().size(); i++) {
            SysYParserParser.CondContext expContext = ctx.cond(i);
            if (expContext instanceof SysYParserParser.COND_EXPContext) {
                SysYParserParser.ExpContext exp = ((SysYParserParser.COND_EXPContext) expContext).exp();
                deal_exp_instanceof_lval(exp, ctx.start.getLine());
            }
        }
    }

    public boolean inValidFunc(Scope scope) {
        while (scope != null && scope != globalScope) {
            if (scope instanceof FunctionSymbolScope && ((FunctionSymbolScope) scope).isFalse)
                return true;
            else
                scope = scope.getEnclosingScope();
        }
        return false;
    }

    public static boolean isNumeric(String str) {
        if (str.length() == 0)
            return false;
        for (int i = str.length(); --i >= 0; ) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
