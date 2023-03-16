import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class LLVMVisitor extends SysYParserBaseVisitor<LLVMValueRef>{
    private GlobalScope globalScope = null;
    private Scope currentScope = null;
    private int localScopeCounter = 0;
    //private Stack<Pair<LLVMBasicBlockRef,LLVMBasicBlockRef>> whileStack = new Stack<>();

    private HashMap<LLVMValueRef, ArrayList<Pair<LLVMBasicBlockRef,LLVMBasicBlockRef>>> whileStack = new HashMap<>();

    public static final BytePointer error = new BytePointer();

    //创建module
    LLVMModuleRef module = LLVMModuleCreateWithName("moudle");

    //初始化IRBuilder，后续将使用这个builder去生成LLVM IR
    LLVMBuilderRef builder = LLVMCreateBuilder();

    //考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
    LLVMTypeRef i32Type = LLVMInt32Type();

    LLVMTypeRef voidType = LLVMVoidType();

    LLVMValueRef zero = LLVMConstInt(i32Type, 0, /* signExtend */ 0);

    LLVMValueRef currentFunc;

    LLVMVisitor(){
        //初始化LLVM
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
    }

    @Override
    public LLVMValueRef visitProgram(SysYParserParser.ProgramContext ctx) {
        globalScope = new GlobalScope(null);
        currentScope = globalScope;

        visitChildren(ctx);

        currentScope = currentScope.getEnclosingScope();
        LLVMPrintModuleToFile(module, Main.store, error);
        return null;
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParserParser.FuncDefContext ctx) {
        String funcName = ctx.IDENT().getText();
        FunctionSymbolScope functionSymbolScope = new FunctionSymbolScope(funcName, currentScope);

        currentScope = functionSymbolScope;
        currentScope.define(functionSymbolScope);
        //生成返回值类型
        LLVMTypeRef returnType = ctx.funcType().getText().equals("int")?i32Type:voidType;
        int size = 0;
        if (ctx.funcFParams()!=null)
            size = ctx.funcFParams().funcFParam().size();
        //生成函数参数类型- all int
        PointerPointer<Pointer> argumentTypes = new PointerPointer<>(size);
        for (int i=0;i<size;i++)
        {
            SysYParserParser.FuncFParamContext funcFParamContext = ctx.funcFParams().funcFParam().get(i);
            if (funcFParamContext.L_BRACKT().size()==0)
            {
                argumentTypes.put(i,i32Type);
                functionSymbolScope.params_Types_name.add(i,"int");
            }
            else
            {
                argumentTypes.put(i,LLVMPointerType(i32Type,0));
                functionSymbolScope.params_Types_name.add(i,"array");
            }

        }

        //生成函数类型
        LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes, /* argumentCount */ size, /* isVariadic */ 0);
        //生成函数，即向之前创建的module中添加函数
        LLVMValueRef function = LLVMAddFunction(module, /*functionName:String*/ctx.IDENT().getText(), ft);
        functionSymbolScope.setRef(function);
        functionSymbolScope.returnT = ctx.funcType().getText();
        globalScope.getSymbols().put(ctx.IDENT().getText(),functionSymbolScope);

        LLVMBasicBlockRef mainEntry = LLVMAppendBasicBlock(function, /*blockName:String*/ctx.IDENT().getText()+"Entry");
        currentFunc = function;
        whileStack.put(function,new ArrayList<>());
        LLVMPositionBuilderAtEnd(builder, mainEntry);

        if (ctx.funcFParams()!=null)
        {
            int count = 0;
            for(SysYParserParser.FuncFParamContext context:ctx.funcFParams().funcFParam()){
                if (context.L_BRACKT().size()==0)
                {
                    //int型变量
                    //申请一块能存放int型的内存
                    LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/context.IDENT().getText());
                    //获取函数的参数
                    LLVMValueRef n = LLVMGetParam(function, /* parameterIndex */count++);
                    //将数值存入该内存
                    LLVMBuildStore(builder, n, pointer);
                    VariableSymbol varSymbol = new VariableSymbol(context.IDENT().getText(), pointer);
                    currentScope.define(varSymbol);
                    varSymbol.setArr(0);
                }
                else
                {
                    LLVMTypeRef vectorType = LLVMPointerType(i32Type,0);
                    LLVMValueRef vectorPointer = LLVMBuildAlloca(builder, vectorType, context.IDENT().getText());
                    LLVMValueRef n = LLVMGetParam(function, /* parameterIndex */count++);
                    LLVMBuildStore(builder, n, vectorPointer);
                    VariableSymbol varSymbol = new VariableSymbol(context.IDENT().getText(), vectorPointer);
                    currentScope.define(varSymbol);
                    varSymbol.setArr(2);
                }

            }
        }

        visitChildren(ctx);

        currentScope = currentScope.getEnclosingScope();
        if (ctx.funcType().getText().equals("void"))
            LLVMBuildRetVoid(builder);
        else
            LLVMBuildRet(builder,zero);
        return null;
    }

    @Override
    public LLVMValueRef visitBlock(SysYParserParser.BlockContext ctx) {
        LocalScope localScope = new LocalScope(currentScope);
        String localScopeName = localScope.getName() + localScopeCounter;
        localScope.setName(localScopeName);
        localScopeCounter++;
        currentScope = localScope;

        visitChildren(ctx);

        currentScope = currentScope.getEnclosingScope();
        return null;
    }

    @Override
    public LLVMValueRef visitVarDecl(SysYParserParser.VarDeclContext ctx) {
        for (SysYParserParser.VarDefContext varDefContext : ctx.varDef())
        {
            if (varDefContext instanceof SysYParserParser.VARDEF_INITContext)
            {
                SysYParserParser.VARDEF_INITContext vardef_initContext = (SysYParserParser.VARDEF_INITContext) varDefContext;
                String name = vardef_initContext.IDENT().getText();
                if (vardef_initContext.L_BRACKT().size()==0) // int
                {
                    if (currentScope==globalScope)
                    {
                        LLVMValueRef globalVar = LLVMAddGlobal(module, i32Type, /*globalVarName:String*/name);
                        LLVMSetInitializer(globalVar,visit(vardef_initContext.initVal().exp()));
                        VariableSymbol varSymbol = new VariableSymbol(name, globalVar);
                        varSymbol.setArr(0);
                        currentScope.define(varSymbol);
                    }
                    else
                    {
                        //int型变量
                        //申请一块能存放int型的内存
                        LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/name);
                        //将数值存入该内存
                        LLVMBuildStore(builder, visit(vardef_initContext.initVal().exp()), pointer);
                        // add to symbolTable
                        VariableSymbol varSymbol = new VariableSymbol(name, pointer);
                        varSymbol.setArr(0);
                        currentScope.define(varSymbol);
                    }

                }
                else  // array
                {
                    if (currentScope==globalScope)
                    {
                        //数组变量
                        SysYParserParser.ConstExpContext constExpContext = vardef_initContext.constExp(0);
                        long length = getValueByRef(visit(constExpContext.exp()));
                        //创建可存放n个int的vector类型
                        LLVMTypeRef vectorType = LLVMArrayType(i32Type, (int) length);
                        LLVMValueRef globalVar = LLVMAddGlobal(module, vectorType, /*globalVarName:String*/name);

                        int size = (int) length;
                        LLVMValueRef[] llvmValueRefs = new LLVMValueRef[size];
                        for (int i=0;i<size;i++)
                        {
                            LLVMValueRef right_value;
                            if (i>=vardef_initContext.initVal().initVal().size())
                            {
                                right_value = LLVMConstInt(i32Type,0,0);
                            }
                            else
                            {
                                SysYParserParser.InitValContext initValContext = vardef_initContext.initVal().initVal().get(i);
                                right_value = visit(initValContext.exp());
                            }
                            llvmValueRefs[i] = right_value;

                        }
                        PointerPointer valuePointer = new PointerPointer(llvmValueRefs);
                        LLVMValueRef valueRef = LLVMConstArray(i32Type,valuePointer, size);
                        LLVMSetInitializer(globalVar,valueRef);
                        // add to symbolTable
                        VariableSymbol varSymbol = new VariableSymbol(name, globalVar);
                        varSymbol.setArr(1);
                        currentScope.define(varSymbol);
                    }
                    else
                    {
                        //数组变量
                        SysYParserParser.ConstExpContext constExpContext = vardef_initContext.constExp(0);
                        long length = getValueByRef(visit(constExpContext.exp()));
                        //创建可存放n个int的vector类型
                        LLVMTypeRef vectorType = LLVMArrayType(i32Type, (int) length);
                        //申请一个可存放该vector类型的内存
                        LLVMValueRef vectorPointer = LLVMBuildAlloca(builder, vectorType, name);

                        int size = (int) length;
                        for (int i=0;i<size;i++)
                        {
                            LLVMValueRef right_value;
                            if (i>=vardef_initContext.initVal().initVal().size())
                            {
                                right_value = LLVMConstInt(i32Type,0,0);
                            }
                            else
                            {
                                SysYParserParser.InitValContext initValContext = vardef_initContext.initVal().initVal().get(i);
                                right_value = visit(initValContext.exp());
                            }
                            PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero,LLVMConstInt(i32Type,i,0)});
                            LLVMValueRef res = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, name);
                            LLVMBuildStore(builder, right_value, res);
                        }
                        // add to symbolTable
                        VariableSymbol varSymbol = new VariableSymbol(name, vectorPointer);
                        varSymbol.setArr(1);
                        currentScope.define(varSymbol);
                    }

                }
            }
            else if (varDefContext instanceof SysYParserParser.VARDEF_NO_INITContext)
            {
                SysYParserParser.VARDEF_NO_INITContext vardef_no_initContext = (SysYParserParser.VARDEF_NO_INITContext) varDefContext;
                String name = vardef_no_initContext.IDENT().getText();
                if (vardef_no_initContext.L_BRACKT().size()==0) // int
                {
                    if (currentScope==globalScope)
                    {
                        LLVMValueRef globalVar = LLVMAddGlobal(module, i32Type, /*globalVarName:String*/name);
                        LLVMSetInitializer(globalVar,LLVMConstInt(i32Type,0,0));
                        VariableSymbol varSymbol = new VariableSymbol(name, globalVar);
                        varSymbol.setArr(0);
                        currentScope.define(varSymbol);
                    }
                    else
                    {
                        //int型变量
                        //申请一块能存放int型的内存
                        LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/name);
                        //将数值存入该内存
                        LLVMBuildStore(builder, LLVMConstInt(i32Type,0,0), pointer);
                        // add to symbolTable
                        VariableSymbol varSymbol = new VariableSymbol(name, pointer);
                        varSymbol.setArr(0);
                        currentScope.define(varSymbol);
                    }
                }
                else
                {
                    if (currentScope==globalScope)
                    {
                        //数组变量
                        SysYParserParser.ConstExpContext constExpContext = vardef_no_initContext.constExp(0);
                        long length = getValueByRef(visit(constExpContext.exp()));
                        //创建可存放n个int的vector类型
                        LLVMTypeRef vectorType = LLVMArrayType(i32Type, (int) length);
                        LLVMValueRef globalVar = LLVMAddGlobal(module, vectorType, /*globalVarName:String*/name);

                        int size = (int) length;
                        LLVMValueRef[] llvmValueRefs = new LLVMValueRef[size];
                        Random random = new Random();
                        for (int i=0;i<size;i++)
                        {
                            llvmValueRefs[i] = LLVMConstInt(i32Type,/*FIXME :zero initializer*/random.nextInt(100),0);
                        }
                        PointerPointer valuePointer = new PointerPointer(llvmValueRefs);
                        LLVMValueRef valueRef = LLVMConstArray(i32Type,valuePointer, size);
                        LLVMSetInitializer(globalVar,valueRef);
                        // add to symbolTable
                        VariableSymbol varSymbol = new VariableSymbol(name, globalVar);
                        varSymbol.setArr(1);
                        currentScope.define(varSymbol);
                    }
                    else
                    {
                        //数组变量
                        SysYParserParser.ConstExpContext constExpContext = vardef_no_initContext.constExp(0);
                        long length = getValueByRef(visit(constExpContext.exp()));
                        //创建可存放n个int的vector类型
                        LLVMTypeRef vectorType = LLVMArrayType(i32Type, (int) length);
                        //申请一个可存放该vector类型的内存
                        LLVMValueRef vectorPointer = LLVMBuildAlloca(builder, vectorType, name);

                        int size = (int) length;
                        for (int i=0;i<size;i++)
                        {
                            LLVMValueRef right_value = LLVMConstInt(i32Type,0,0);
                            PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero,LLVMConstInt(i32Type,i,0)});
                            LLVMValueRef res = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, name);
                            LLVMBuildStore(builder, right_value, res);
                        }
                        // add to symbolTable
                        VariableSymbol varSymbol = new VariableSymbol(name, vectorPointer);
                        varSymbol.setArr(1);
                        currentScope.define(varSymbol);
                    }

                }

            }
        }
        return null;
    }

    @Override
    public LLVMValueRef visitConstDecl(SysYParserParser.ConstDeclContext ctx) {
        for (SysYParserParser.ConstDefContext constDefContext:ctx.constDef())
        {
            String name = constDefContext.IDENT().getText();
            if (constDefContext.L_BRACKT().size()==0) // int
            {
                if (currentScope==globalScope)
                {
                    LLVMValueRef globalVar = LLVMAddGlobal(module, i32Type, /*globalVarName:String*/name);
                    LLVMSetInitializer(globalVar,visit(constDefContext.constInitVal().constExp().exp()));
                    VariableSymbol varSymbol = new VariableSymbol(name, globalVar);
                    varSymbol.setArr(0);
                    currentScope.define(varSymbol);
                }
                else
                {
                    //int型变量
                    //申请一块能存放int型的内存
                    LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/name);
                    //将数值存入该内存
                    LLVMBuildStore(builder, visit(constDefContext.constInitVal().constExp().exp()), pointer);
                    // add to symbolTable
                    VariableSymbol varSymbol = new VariableSymbol(name, pointer);
                    varSymbol.setArr(0);
                    currentScope.define(varSymbol);
                }
            }
            else  // array
            {
                if (currentScope==globalScope)
                {
                    //数组变量
                    SysYParserParser.ConstExpContext constExpContext = constDefContext.constExp(0);
                    long length = getValueByRef(visit(constExpContext.exp()));
                    //创建可存放n个int的vector类型
                    LLVMTypeRef vectorType = LLVMArrayType(i32Type, (int) length);
                    LLVMValueRef globalVar = LLVMAddGlobal(module, vectorType, /*globalVarName:String*/name);

                    int size = (int) length;
                    LLVMValueRef[] llvmValueRefs = new LLVMValueRef[size];
                    for (int i=0;i<size;i++)
                    {
                        LLVMValueRef right_value;
                        if (i>=constDefContext.constInitVal().constInitVal().size())
                        {
                            right_value = LLVMConstInt(i32Type,0,0);
                        }
                        else
                        {
                            SysYParserParser.ConstInitValContext initValContext = constDefContext.constInitVal().constInitVal().get(i);
                            right_value = visit(initValContext.constExp().exp());
                        }
                        llvmValueRefs[i] = right_value;

                    }
                    PointerPointer valuePointer = new PointerPointer(llvmValueRefs);
                    LLVMValueRef valueRef = LLVMConstArray(i32Type,valuePointer, size);
                    LLVMSetInitializer(globalVar,valueRef);
                    // add to symbolTable
                    VariableSymbol varSymbol = new VariableSymbol(name, globalVar);
                    varSymbol.setArr(1);
                    currentScope.define(varSymbol);
                }
                else
                {
                    //数组变量
                    SysYParserParser.ConstExpContext constExpContext = constDefContext.constExp(0);
                    long length = getValueByRef(visit(constExpContext.exp()));
                    //创建可存放n个int的vector类型
                    LLVMTypeRef vectorType = LLVMArrayType(i32Type, (int) length);
                    //申请一个可存放该vector类型的内存
                    LLVMValueRef vectorPointer = LLVMBuildAlloca(builder, vectorType, name);

                    int size = (int) length;
                    for (int i=0;i<size;i++)
                    {
                        LLVMValueRef right_value;
                        if (i>=constDefContext.constInitVal().constInitVal().size())
                        {
                            right_value = LLVMConstInt(i32Type,0,0);
                        }
                        else
                        {
                            SysYParserParser.ConstInitValContext initValContext = constDefContext.constInitVal().constInitVal().get(i);
                            right_value = visit(initValContext.constExp().exp());
                        }
                        PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero,LLVMConstInt(i32Type,i,0)});
                        LLVMValueRef res = LLVMBuildGEP(builder, vectorPointer, valuePointer, 2, name);
                        LLVMBuildStore(builder, right_value, res);
                    }
                    // add to symbolTable
                    VariableSymbol varSymbol = new VariableSymbol(name, vectorPointer);
                    varSymbol.setArr(1);
                    currentScope.define(varSymbol);
                }

            }
        }
        return null;
    }

    @Override
    public LLVMValueRef visitSTMT_RETURN(SysYParserParser.STMT_RETURNContext ctx) {
        SysYParserParser.ExpContext exp = ctx.exp();
        if (exp!=null)
            LLVMBuildRet(builder, visit(exp));
        else
            LLVMBuildRet(builder,null);
        return null;
    }

    @Override
    public LLVMValueRef visitSTMT_ASSIGN(SysYParserParser.STMT_ASSIGNContext ctx) {
        SysYParserParser.ExpContext exp = ctx.exp();
        LLVMValueRef right_val = visit(exp);

        String name = ctx.lVal().IDENT().getText();
        Symbol resolve = currentScope.resolve(name);
        LLVMValueRef ref = resolve.getRef();
        if (resolve.getArr()==0)
        {
            //resolve.setRef(right_val);
            LLVMBuildStore(builder, right_val, ref);
            return right_val;
        }
        else
        {
            if (ctx.lVal().L_BRACKT().size()==0)
            {
                //resolve.setRef(right_val);
                LLVMBuildStore(builder, right_val, ref);
            }
            else
            {
                if (resolve.getArr()==2)
                {
                    LLVMValueRef valueRef = LLVMBuildLoad(builder, ref, name);
                    SysYParserParser.ExpContext index_exp = ctx.lVal().exp(0);
                    LLVMValueRef index_ref = visit(index_exp);
                    PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{index_ref});
                    LLVMValueRef res = LLVMBuildGEP(builder, valueRef, valuePointer, 1, name);
                    LLVMBuildStore(builder, right_val, res);
                }
                else
                {
                    SysYParserParser.ExpContext index_exp = ctx.lVal().exp(0);
                    LLVMValueRef index_ref = visit(index_exp);
                    PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero,index_ref});
                    LLVMValueRef res = LLVMBuildGEP(builder, ref, valuePointer, 2, name);
                    LLVMBuildStore(builder, right_val, res);
                }

            }
            return right_val;
        }
    }

    @Override
    public LLVMValueRef visitSTMT_IF(SysYParserParser.STMT_IFContext ctx) {
        LLVMValueRef res_ref = visit(ctx.cond());
        LLVMValueRef condition = LLVMBuildICmp(builder, /*这是个int型常量，表示比较的方式*/LLVMIntNE, res_ref, zero, "if_cond");

        LLVMBasicBlockRef if_true = LLVMAppendBasicBlock(currentFunc, /*blockName:String*/"if_true");
        LLVMBasicBlockRef if_false = LLVMAppendBasicBlock(currentFunc, /*blockName:String*/"if_false");

        LLVMBuildCondBr(builder,
                /*condition:LLVMValueRef*/ condition,
                /*ifTrue:LLVMBasicBlockRef*/ if_true,
                /*ifFalse:LLVMBasicBlockRef*/ if_false);

        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(currentFunc, /*blockName:String*/"if_end");

        LLVMPositionBuilderAtEnd(builder, if_true);
        visit(ctx.stmt(0));
        LLVMBuildBr(builder,entry);

        LLVMPositionBuilderAtEnd(builder, if_false);
        if (ctx.ELSE()!=null)
            visit(ctx.stmt(1));
        LLVMBuildBr(builder,entry);

        LLVMPositionBuilderAtEnd(builder, entry);
        return null;
    }

    @Override
    public LLVMValueRef visitSTMT_WHILE(SysYParserParser.STMT_WHILEContext ctx) {
        LLVMBasicBlockRef while_check = LLVMAppendBasicBlock(currentFunc, /*blockName:String*/"while_check");
        LLVMBasicBlockRef inter = LLVMAppendBasicBlock(currentFunc, /*blockName:String*/"while_inter");
        LLVMBasicBlockRef while_end = LLVMAppendBasicBlock(currentFunc, /*blockName:String*/"while_end");
        whileStack.get(currentFunc).add(new Pair<>(while_check,while_end));
        LLVMBuildBr(builder, while_check);

        LLVMPositionBuilderAtEnd(builder, while_check);
        LLVMValueRef while_cond = LLVMBuildICmp(builder,/*这是个int型常量，表示比较的方式*/LLVMIntNE, visit(ctx.cond()), zero, "while_cond");
        LLVMBuildCondBr(builder,
                /*condition:LLVMValueRef*/while_cond,
                /*ifTrue:LLVMBasicBlockRef*/inter,
                /*ifFalse:LLVMBasicBlockRef*/while_end);

        LLVMPositionBuilderAtEnd(builder, inter);
        visit(ctx.stmt());

        LLVMBuildBr(builder, while_check);

        LLVMPositionBuilderAtEnd(builder, while_end);
        whileStack.get(currentFunc).remove(whileStack.get(currentFunc).size()-1);

        return null;
    }
    @Override
    public LLVMValueRef visitSTMT_CONTINUE(SysYParserParser.STMT_CONTINUEContext ctx) {
        LLVMBuildBr(builder, whileStack.get(currentFunc).get(whileStack.get(currentFunc).size()-1).a);
        return null;
    }
    @Override
    public LLVMValueRef visitSTMT_BREAK(SysYParserParser.STMT_BREAKContext ctx) {
        LLVMBuildBr(builder, whileStack.get(currentFunc).get(whileStack.get(currentFunc).size()-1).b);
        return null;
    }

    @Override
    public LLVMValueRef visitL_EXP_R(SysYParserParser.L_EXP_RContext ctx) {
        SysYParserParser.ExpContext exp = ctx.exp();
        return visit(exp);
    }

    @Override
    public LLVMValueRef visitEXP_MUL_EXP(SysYParserParser.EXP_MUL_EXPContext ctx) {
        LLVMValueRef left = visit(ctx.exp(0));
        LLVMValueRef right = visit(ctx.exp(1));

        if (ctx.MUL() != null)
            return LLVMBuildMul(builder,left,right,"mul");
        if (ctx.DIV() != null)
            return LLVMBuildSDiv(builder,left,right,"div");
        if (ctx.MOD() != null)
            return LLVMBuildSRem(builder,left,right,"mod");
        return null;
    }

    @Override
    public LLVMValueRef visitUNARY_EXP(SysYParserParser.UNARY_EXPContext ctx) {
        LLVMValueRef exp = visit(ctx.exp());

        if (ctx.unaryOp().PLUS() != null)
            return exp;
        if (ctx.unaryOp().MINUS() != null)
           return LLVMBuildSub(builder,zero,exp,"sub_u");
        if (ctx.unaryOp().NOT() != null)
        {
            // 生成icmp
            exp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(i32Type, 0, 0), exp, "tmp_");
            // 生成xor
            exp = LLVMBuildXor(builder, exp, LLVMConstInt(LLVMInt1Type(), 1, 0), "tmp_");
            // 生成zext
            exp = LLVMBuildZExt(builder, exp, i32Type, "tmp_");
            return exp;
        }
        return null;
    }

    @Override
    public LLVMValueRef visitEXP_PLUS_EXP(SysYParserParser.EXP_PLUS_EXPContext ctx) {
        LLVMValueRef left = visit(ctx.exp(0));
        LLVMValueRef right = visit(ctx.exp(1));

        if (ctx.PLUS() != null)
            return LLVMBuildAdd(builder,left,right,"add");
        else if (ctx.MINUS() != null)
            return LLVMBuildSub(builder,left,right,"sub");
        return null;
    }

    @Override
    public LLVMValueRef visitEXP_NUM(SysYParserParser.EXP_NUMContext ctx) {
        long value;
        String temp = ctx.number().getText();
        if (temp.startsWith("0x") || temp.startsWith("0X"))
            value = Integer.parseInt(temp.substring(2), 16);
        else if (temp.startsWith("0") && temp.length() >= 2)
            value = Integer.parseInt(temp, 8);
        else
            value = Integer.parseInt(temp);
        return LLVMConstInt(i32Type, value, /* signExtend */ 0);
    }

    @Override
    public LLVMValueRef visitEXP_LVAL(SysYParserParser.EXP_LVALContext ctx) {
        Symbol symbol = currentScope.resolve(ctx.lVal().IDENT().getText());
        LLVMValueRef ref = symbol.getRef();
        if (ctx.lVal().L_BRACKT().size()==0)
        {
            if (symbol.getArr()==0)
                return LLVMBuildLoad(builder, ref, /*varName:String*/symbol.getName());
            else
                return ref;
        }
        else
        {
            LLVMValueRef llvmValueRef = visit(ctx.lVal().exp(0));
            if (symbol.getArr()==2)
            {
                LLVMValueRef valueRef = LLVMBuildLoad(builder, ref, /*varName:String*/symbol.getName());
                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{llvmValueRef});
                LLVMValueRef res = LLVMBuildGEP(builder, valueRef, valuePointer, 1, ctx.lVal().IDENT().getText());
                return LLVMBuildLoad(builder, res, /*varName:String*/symbol.getName());
            }
            else
            {
                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero,llvmValueRef});
                LLVMValueRef res = LLVMBuildGEP(builder, ref, valuePointer, 2, ctx.lVal().IDENT().getText());
                return LLVMBuildLoad(builder, res, /*varName:String*/symbol.getName());
            }

        }
    }

    @Override
    public LLVMValueRef visitEXP_FUNC_ARGUMENT(SysYParserParser.EXP_FUNC_ARGUMENTContext ctx) {
        Symbol symbol = globalScope.getSymbol(ctx.IDENT().getText());
        if (symbol instanceof FunctionSymbolScope)
        {
            LLVMValueRef ref = symbol.getRef();
            int size = 0;
            LLVMValueRef[] params = new LLVMValueRef[0];
            int i=0;
            if (ctx.funcRParams()!=null)
            {
                size = ctx.funcRParams().param().size();
                params = new LLVMValueRef[size];
                for (SysYParserParser.ParamContext paramContext:ctx.funcRParams().param())
                {
                    SysYParserParser.ExpContext exp = paramContext.exp();
                    LLVMValueRef llvmValueRef = visit(exp);
                    LLVMValueRef res = llvmValueRef;
                    String param_type = ((FunctionSymbolScope) symbol).params_Types_name.get(i);
                    if (param_type.equals("array"))
                    {
                        Symbol resolve = currentScope.resolve(paramContext.exp().getText());
                        if (resolve!=null)
                        {
                            if (resolve.getArr()==2)
                            {
                                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero});
                                res = LLVMBuildGEP(builder, llvmValueRef, valuePointer, 1, paramContext.exp().getText());
                                res = LLVMBuildLoad(builder,res,paramContext.exp().getText());
                            }
                            else
                            {
                                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero,zero});
                                res = LLVMBuildGEP(builder, llvmValueRef, valuePointer, 2, paramContext.exp().getText());
                            }

                        }

                    }
                    params[i++] = res;
                }
            }
            PointerPointer valuePointer = new PointerPointer(params);
            if(((FunctionSymbolScope) symbol).returnT.equals("void"))
                return LLVMBuildCall(builder,ref,valuePointer,size,"");
            return LLVMBuildCall(builder,ref,valuePointer,size,ctx.IDENT().getText());
        }
        return null;
    }

    @Override
    public LLVMValueRef visitCOND_EXP(SysYParserParser.COND_EXPContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public LLVMValueRef visitCOND_OR(SysYParserParser.COND_ORContext ctx) {
        return LLVMBuildOr(builder, visit(ctx.cond(0)), visit(ctx.cond(1)), "or");
    }

    @Override
    public LLVMValueRef visitCOND_AND(SysYParserParser.COND_ANDContext ctx) {
        return LLVMBuildAnd(builder, visit(ctx.cond(0)), visit(ctx.cond(1)), "and");
    }

    @Override
    public LLVMValueRef visitCOND_LT(SysYParserParser.COND_LTContext ctx) {
        LLVMValueRef left_ref = visit(ctx.cond(0));
        LLVMValueRef right_ref = visit(ctx.cond(1));

        if (ctx.GE()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntSGE, left_ref, right_ref, "ge");
            return LLVMBuildZExt(builder, valueRef, i32Type, "z_ge");
        }
        else if (ctx.GT()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntSGT, left_ref, right_ref, "gt");
            return LLVMBuildZExt(builder, valueRef, i32Type, "z_gt");
        }
        else if (ctx.LT()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntSLT, left_ref, right_ref, "lt");
            return LLVMBuildZExt(builder, valueRef, i32Type, "z_lt");
        }
        else if (ctx.LE()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntSLE, left_ref, right_ref, "le");
            return LLVMBuildZExt(builder, valueRef, i32Type, "z_le");
        }
        return null;
    }

    @Override
    public LLVMValueRef visitCOND_EQ(SysYParserParser.COND_EQContext ctx) {
        LLVMValueRef left_ref = visit(ctx.cond(0));
        LLVMValueRef right_ref = visit(ctx.cond(1));

        if (ctx.EQ()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntEQ, left_ref, right_ref, "eq");
            return LLVMBuildZExt(builder, valueRef, i32Type, "z_eq");
        }
        else if (ctx.NEQ()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntNE, left_ref, right_ref, "neq");
            return LLVMBuildZExt(builder, valueRef, i32Type, "z_neq");
        }
        return null;
    }

    public long getValueByRef(LLVMValueRef llvmValueRef) {
        if (LLVMGetTypeKind(LLVMTypeOf(llvmValueRef)) == LLVMIntegerTypeKind)
            return LLVMConstIntGetSExtValue(llvmValueRef);
        LLVMValueRef value = LLVMBuildLoad(builder, llvmValueRef, /*varName:String*/"value");
        return getValueByRef(value);
    }
}
