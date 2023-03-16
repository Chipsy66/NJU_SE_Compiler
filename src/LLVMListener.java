import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.Random;

import static org.bytedeco.llvm.global.LLVM.*;

public class LLVMListener extends SysYParserBaseListener{

    private GlobalScope globalScope = null;
    private Scope currentScope = null;
    private int localScopeCounter = 0;

    private final ParseTreeProperty<LLVMValueRef> returnInfo = new ParseTreeProperty<>();

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

    LLVMListener() {
        //初始化LLVM
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
    }

    @Override
    public void enterProgram(SysYParserParser.ProgramContext ctx) { // enter a new scope
        globalScope = new GlobalScope(null);
        currentScope = globalScope;
    }
    public void enterFuncDef(SysYParserParser.FuncDefContext ctx) {// enter a new scope, and define a symbol
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
            argumentTypes.put(i,i32Type);
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
        LLVMPositionBuilderAtEnd(builder, mainEntry);

        if (ctx.funcFParams()!=null)
        {
            int count = 0;
            for(SysYParserParser.FuncFParamContext context:ctx.funcFParams().funcFParam()){
                //int型变量
                //申请一块能存放int型的内存
                LLVMValueRef pointer = LLVMBuildAlloca(builder, i32Type, /*pointerName:String*/context.IDENT().getText());
                //获取函数的参数
                LLVMValueRef n = LLVMGetParam(function, /* parameterIndex */count++);
                //将数值存入该内存
                LLVMBuildStore(builder, n, pointer);
                VariableSymbol varSymbol = new VariableSymbol(context.IDENT().getText(), pointer);
                currentScope.define(varSymbol);
            }
        }

    }
    @Override
    public void enterBlock(SysYParserParser.BlockContext ctx) {// enter a new scope
        LocalScope localScope = new LocalScope(currentScope);
        String localScopeName = localScope.getName() + localScopeCounter;
        localScope.setName(localScopeName);
        localScopeCounter++;
        currentScope = localScope;
    }

    @Override
    public void exitProgram(SysYParserParser.ProgramContext ctx) { // exit a scope
        currentScope = currentScope.getEnclosingScope();
        LLVMPrintModuleToFile(module, Main.store, error);
        // LLVMDumpModule(module);

    }

    @Override
    public void exitFuncDef(SysYParserParser.FuncDefContext ctx) { // exit a scope
        currentScope = currentScope.getEnclosingScope();
        int a = 1;
        for (int i = 0; i < ctx.block().blockItem().size(); i++) {
            SysYParserParser.BlockItemContext blockItemContext = ctx.block().blockItem(i);
            if (blockItemContext.stmt() != null && blockItemContext.stmt() instanceof SysYParserParser.STMT_RETURNContext) {
                a = 0;
            }
        }
        if (a==1)
            LLVMBuildRetVoid(builder);
    }

    @Override
    public void exitBlock(SysYParserParser.BlockContext ctx) { // exit a scope
        currentScope = currentScope.getEnclosingScope();
    }

    @Override
    public void exitVarDecl(SysYParserParser.VarDeclContext ctx) {
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
                        LLVMSetInitializer(globalVar,returnInfo.get(vardef_initContext.initVal().exp()));
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
                        LLVMBuildStore(builder, returnInfo.get(vardef_initContext.initVal().exp()), pointer);
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
                        long length = getValueByRef(returnInfo.get(constExpContext.exp()));
                        //创建可存放n个int的vector类型
                        LLVMTypeRef vectorType = LLVMVectorType(i32Type, (int) length);
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
                                right_value = returnInfo.get(initValContext.exp());
                            }
                            llvmValueRefs[i] = right_value;

                        }
                        PointerPointer valuePointer = new PointerPointer(llvmValueRefs);
                        LLVMValueRef valueRef = LLVMConstVector(valuePointer, size);
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
                        long length = getValueByRef(returnInfo.get(constExpContext.exp()));
                        //创建可存放n个int的vector类型
                        LLVMTypeRef vectorType = LLVMVectorType(i32Type, (int) length);
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
                                right_value = returnInfo.get(initValContext.exp());
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
                        long length = getValueByRef(returnInfo.get(constExpContext.exp()));
                        //创建可存放n个int的vector类型
                        LLVMTypeRef vectorType = LLVMVectorType(i32Type, (int) length);
                        LLVMValueRef globalVar = LLVMAddGlobal(module, vectorType, /*globalVarName:String*/name);

                        int size = (int) length;
                        LLVMValueRef[] llvmValueRefs = new LLVMValueRef[size];
                        Random random = new Random();
                        for (int i=0;i<size;i++)
                        {
                            llvmValueRefs[i] = LLVMConstInt(i32Type,/*FIXME :zero initializer*/random.nextInt(100),0);
                        }
                        PointerPointer valuePointer = new PointerPointer(llvmValueRefs);
                        LLVMValueRef valueRef = LLVMConstVector(valuePointer, size);
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
                        long length = getValueByRef(returnInfo.get(constExpContext.exp()));
                        //创建可存放n个int的vector类型
                        LLVMTypeRef vectorType = LLVMVectorType(i32Type, (int) length);
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

    }

    @Override
    public void exitConstDecl(SysYParserParser.ConstDeclContext ctx) {
        for (SysYParserParser.ConstDefContext constDefContext:ctx.constDef())
        {
            String name = constDefContext.IDENT().getText();
            if (constDefContext.L_BRACKT().size()==0) // int
            {
                if (currentScope==globalScope)
                {
                    LLVMValueRef globalVar = LLVMAddGlobal(module, i32Type, /*globalVarName:String*/name);
                    LLVMSetInitializer(globalVar,returnInfo.get(constDefContext.constInitVal().constExp().exp()));
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
                    LLVMBuildStore(builder, returnInfo.get(constDefContext.constInitVal().constExp().exp()), pointer);
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
                    long length = getValueByRef(returnInfo.get(constExpContext.exp()));
                    //创建可存放n个int的vector类型
                    LLVMTypeRef vectorType = LLVMVectorType(i32Type, (int) length);
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
                            right_value = returnInfo.get(initValContext.constExp().exp());
                        }
                        llvmValueRefs[i] = right_value;

                    }
                    PointerPointer valuePointer = new PointerPointer(llvmValueRefs);
                    LLVMValueRef valueRef = LLVMConstVector(valuePointer, size);
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
                    long length = getValueByRef(returnInfo.get(constExpContext.exp()));
                    //创建可存放n个int的vector类型
                    LLVMTypeRef vectorType = LLVMVectorType(i32Type, (int) length);
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
                            right_value = returnInfo.get(initValContext.constExp().exp());
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

    }

    @Override
    public void exitSTMT_RETURN(SysYParserParser.STMT_RETURNContext ctx) {
        SysYParserParser.ExpContext exp = ctx.exp();
        LLVMValueRef returnRef = returnInfo.get(exp);
        LLVMBuildRet(builder, returnRef);
    }

    @Override
    public void exitSTMT_ASSIGN(SysYParserParser.STMT_ASSIGNContext ctx) {
        SysYParserParser.ExpContext exp = ctx.exp();
        LLVMValueRef right_val = returnInfo.get(exp);

        String name = ctx.lVal().IDENT().getText();
        Symbol resolve = currentScope.resolve(name);
        LLVMValueRef ref = resolve.getRef();
        if (resolve.getArr()==0)
        {
            //resolve.setRef(right_val);
            returnInfo.put(ctx.lVal(),right_val);
            LLVMBuildStore(builder, right_val, ref);
        }
        else
        {
            if (ctx.lVal().L_BRACKT().size()==0)
            {
                //resolve.setRef(right_val);
                returnInfo.put(ctx.lVal(),right_val);
                LLVMBuildStore(builder, right_val, ref);
            }
            else
            {
                SysYParserParser.ExpContext index_exp = ctx.lVal().exp(0);
                LLVMValueRef index_ref = returnInfo.get(index_exp);
                PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero,index_ref});
                LLVMValueRef res = LLVMBuildGEP(builder, ref, valuePointer, 2, "");
                returnInfo.put(ctx.lVal(),right_val);
                LLVMBuildStore(builder, right_val, res);
            }
        }

    }

    @Override
    public void enterSTMT_IF(SysYParserParser.STMT_IFContext ctx) {

    }

    @Override
    public void exitSTMT_IF(SysYParserParser.STMT_IFContext ctx) {
        LLVMValueRef res_ref = returnInfo.get(ctx.cond());
        LLVMValueRef condition = LLVMBuildICmp(builder, /*这是个int型常量，表示比较的方式*/LLVMIntNE, res_ref, zero, "if_cond");

    }

    @Override
    public void exitL_EXP_R(SysYParserParser.L_EXP_RContext ctx) {
        SysYParserParser.ExpContext exp = ctx.exp();
        returnInfo.put(ctx,returnInfo.get(exp));
    }

    @Override
    public void exitEXP_MUL_EXP(SysYParserParser.EXP_MUL_EXPContext ctx) {
        LLVMValueRef left = returnInfo.get(ctx.exp(0));
        LLVMValueRef right = returnInfo.get(ctx.exp(1));

        if (ctx.MUL() != null)
            returnInfo.put(ctx,LLVMBuildMul(builder,left,right,""));
        if (ctx.DIV() != null)
            returnInfo.put(ctx,LLVMBuildSDiv(builder,left,right,""));
        if (ctx.MOD() != null)
            returnInfo.put(ctx,LLVMBuildSRem(builder,left,right,""));
    }

    @Override
    public void exitUNARY_EXP(SysYParserParser.UNARY_EXPContext ctx) {
        LLVMValueRef exp = returnInfo.get(ctx.exp());

        if (ctx.unaryOp().PLUS() != null)
            returnInfo.put(ctx,exp);
        if (ctx.unaryOp().MINUS() != null)
            returnInfo.put(ctx,LLVMBuildSub(builder,zero,exp,""));
        if (ctx.unaryOp().NOT() != null)
        {
            // 生成icmp
            exp = LLVMBuildICmp(builder, LLVMIntNE, LLVMConstInt(i32Type, 0, 0), exp, "tmp_");
            // 生成xor
            exp = LLVMBuildXor(builder, exp, LLVMConstInt(LLVMInt1Type(), 1, 0), "tmp_");
            // 生成zext
            exp = LLVMBuildZExt(builder, exp, i32Type, "tmp_");
            returnInfo.put(ctx,exp);
        }

    }

    @Override
    public void exitEXP_PLUS_EXP(SysYParserParser.EXP_PLUS_EXPContext ctx) {
        LLVMValueRef left = returnInfo.get(ctx.exp(0));
        LLVMValueRef right = returnInfo.get(ctx.exp(1));

        if (ctx.PLUS() != null)
            returnInfo.put(ctx,LLVMBuildAdd(builder,left,right,""));
        if (ctx.MINUS() != null)
            returnInfo.put(ctx,LLVMBuildSub(builder,left,right,""));
    }

    @Override
    public void exitEXP_NUM(SysYParserParser.EXP_NUMContext ctx) {
        long value;
        String temp = ctx.number().getText();
        if (temp.startsWith("0x") || temp.startsWith("0X"))
            value = Integer.parseInt(temp.substring(2), 16);
        else if (temp.startsWith("0") && temp.length() >= 2)
            value = Integer.parseInt(temp, 8);
        else
            value = Integer.parseInt(temp);
        returnInfo.put(ctx,LLVMConstInt(i32Type, value, /* signExtend */ 0));
        if (ctx.getText().equals("45"))
            System.out.println(returnInfo.get(ctx));

    }

    @Override
    public void exitEXP_LVAL(SysYParserParser.EXP_LVALContext ctx) {
        Symbol symbol = currentScope.resolve(ctx.lVal().IDENT().getText());
        LLVMValueRef ref = symbol.getRef();
        if (ctx.lVal().L_BRACKT().size()==0)
        {
            LLVMValueRef valueRef = LLVMBuildLoad(builder, ref, /*varName:String*/"");
            returnInfo.put(ctx,valueRef);
        }
        else
        {
            LLVMValueRef llvmValueRef = returnInfo.get(ctx.lVal().exp(0));
            PointerPointer valuePointer = new PointerPointer(new LLVMValueRef[]{zero,llvmValueRef});
            LLVMValueRef res = LLVMBuildGEP(builder, ref, valuePointer, 2, ctx.lVal().IDENT().getText());
            LLVMValueRef valueRef = LLVMBuildLoad(builder, res, /*varName:String*/"");
            returnInfo.put(ctx,valueRef);
        }

    }

    @Override
    public void exitEXP_FUNC_ARGUMENT(SysYParserParser.EXP_FUNC_ARGUMENTContext ctx) {
        Symbol symbol = globalScope.getSymbol(ctx.IDENT().getText());
        if (symbol instanceof FunctionSymbolScope)
        {
            if(((FunctionSymbolScope) symbol).returnT.equals("void"))
                return ;
            LLVMValueRef ref = ((FunctionSymbolScope) symbol).getRef();
            int size = 0;
            LLVMValueRef[] params = new LLVMValueRef[1];
            int i=0;
            if (ctx.funcRParams()!=null)
            {
                size = ctx.funcRParams().param().size();
                params = new LLVMValueRef[size];
                for (SysYParserParser.ParamContext paramContext:ctx.funcRParams().param())
                {
                    SysYParserParser.ExpContext exp = paramContext.exp();
                    LLVMValueRef llvmValueRef = returnInfo.get(exp);
                    params[i++] = llvmValueRef;
                }
            }
            PointerPointer valuePointer = new PointerPointer(params);
            returnInfo.put(ctx,LLVMBuildCall(builder,ref,valuePointer,size,ctx.IDENT().getText()));
        }

    }

    @Override
    public void exitCOND_EXP(SysYParserParser.COND_EXPContext ctx) {
        returnInfo.put(ctx,returnInfo.get(ctx.exp()));
    }

    @Override
    public void exitCOND_OR(SysYParserParser.COND_ORContext ctx) {
        LLVMValueRef valueRef = LLVMBuildOr(builder, returnInfo.get(ctx.cond(0)), returnInfo.get(ctx.cond(1)), "or");
        returnInfo.put(ctx,valueRef);
    }

    @Override
    public void exitCOND_AND(SysYParserParser.COND_ANDContext ctx) {
        LLVMValueRef valueRef = LLVMBuildAnd(builder, returnInfo.get(ctx.cond(0)), returnInfo.get(ctx.cond(1)), "and");
        returnInfo.put(ctx,valueRef);
    }

    @Override
    public void exitCOND_LT(SysYParserParser.COND_LTContext ctx) {
        LLVMValueRef left_ref = returnInfo.get(ctx.cond(0));
        LLVMValueRef right_ref = returnInfo.get(ctx.cond(1));

        if (ctx.GE()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntSGE, left_ref, right_ref, "ge");
            LLVMValueRef zExt = LLVMBuildZExt(builder, valueRef, i32Type, "");
            returnInfo.put(ctx,zExt);
        }
        else if (ctx.GT()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntSGT, left_ref, right_ref, "gt");
            LLVMValueRef zExt = LLVMBuildZExt(builder, valueRef, i32Type, "");
            returnInfo.put(ctx,zExt);
        }
        else if (ctx.LT()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntSLT, left_ref, right_ref, "lt");
            LLVMValueRef zExt = LLVMBuildZExt(builder, valueRef, i32Type, "");
            returnInfo.put(ctx,zExt);
        }
        else if (ctx.LE()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntSLE, left_ref, right_ref, "le");
            LLVMValueRef zExt = LLVMBuildZExt(builder, valueRef, i32Type, "");
            returnInfo.put(ctx,zExt);
        }
    }

    @Override
    public void exitCOND_EQ(SysYParserParser.COND_EQContext ctx) {
        LLVMValueRef left_ref = returnInfo.get(ctx.cond(0));
        LLVMValueRef right_ref = returnInfo.get(ctx.cond(1));

        if (ctx.EQ()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntEQ, left_ref, right_ref, "eq");
            LLVMValueRef zExt = LLVMBuildZExt(builder, valueRef, i32Type, "");
            returnInfo.put(ctx,zExt);
        }
        else if (ctx.NEQ()!=null)
        {
            LLVMValueRef valueRef = LLVMBuildICmp(builder, LLVMIntNE, left_ref, right_ref, "neq");
            LLVMValueRef zExt = LLVMBuildZExt(builder, valueRef, i32Type, "");
            returnInfo.put(ctx,zExt);
        }
    }

    public long getValueByRef(LLVMValueRef llvmValueRef) {
        if (LLVMGetTypeKind(LLVMTypeOf(llvmValueRef)) == LLVMIntegerTypeKind)
            return LLVMConstIntGetSExtValue(llvmValueRef);
        LLVMValueRef value = LLVMBuildLoad(builder, llvmValueRef, /*varName:String*/"value");
        return getValueByRef(value);
    }
}