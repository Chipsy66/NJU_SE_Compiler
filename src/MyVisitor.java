import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class MyVisitor extends SysYParserBaseVisitor<LLVMValueRef> {

    public static final BytePointer error = new BytePointer();



    //创建module
    LLVMModuleRef module = LLVMModuleCreateWithName("moudle");

    //初始化IRBuilder，后续将使用这个builder去生成LLVM IR
    LLVMBuilderRef builder = LLVMCreateBuilder();

    //考虑到我们的语言中仅存在int一个基本类型，可以通过下面的语句为LLVM的int型重命名方便以后使用
    LLVMTypeRef i32Type = LLVMInt32Type();

    MyVisitor() {
        //初始化LLVM
        LLVMInitializeCore(LLVMGetGlobalPassRegistry());
        LLVMLinkInMCJIT();
        LLVMInitializeNativeAsmPrinter();
        LLVMInitializeNativeAsmParser();
        LLVMInitializeNativeTarget();
    }

    @Override
    public LLVMValueRef visitFuncDef(SysYParserParser.FuncDefContext ctx) {
        //生成返回值类型
        LLVMTypeRef returnType = i32Type;

        //生成函数参数类型
        PointerPointer<Pointer> argumentTypes = new PointerPointer<>(0);

        //生成函数类型
        LLVMTypeRef ft = LLVMFunctionType(returnType, argumentTypes, /* argumentCount */ 0, /* isVariadic */ 0);
        //生成函数，即向之前创建的module中添加函数
        LLVMValueRef function = LLVMAddFunction(module, /*functionName:String*/ctx.IDENT().getText(), ft);

        LLVMBasicBlockRef mainEntry = LLVMAppendBasicBlock(function, /*blockName:String*/"mainEntry");
        LLVMPositionBuilderAtEnd(builder, mainEntry);
        long val = 0L;

        for (int i = 0; i < ctx.block().blockItem().size(); i++) {
            SysYParserParser.BlockItemContext blockItemContext = ctx.block().blockItem(i);
            if (blockItemContext.stmt() != null && blockItemContext.stmt() instanceof SysYParserParser.STMT_RETURNContext) {
                SysYParserParser.ExpContext exp = ((SysYParserParser.STMT_RETURNContext) blockItemContext.stmt()).exp();
                LLVMValueRef exp_value_ref = exp.accept(this);
                val = getValueByRef(exp_value_ref); // TODO
            }
        }
        LLVMValueRef res = LLVMConstInt(i32Type, val, /* signExtend */ 0);
        LLVMBuildRet(builder, res);

        //LLVMDumpModule(module);

        LLVMPrintModuleToFile(module, Main.store, error);

        return function;
    }

    @Override
    public LLVMValueRef visitL_EXP_R(SysYParserParser.L_EXP_RContext ctx) {
        SysYParserParser.ExpContext exp = ctx.exp();
        return exp.accept(this);
    }

    @Override
    public LLVMValueRef visitEXP_MUL_EXP(SysYParserParser.EXP_MUL_EXPContext ctx) {
        LLVMValueRef left = ctx.exp(0).accept(this);
        LLVMValueRef right = ctx.exp(1).accept(this);

        long left_val = getValueByRef(left); // TODO
        long right_val = getValueByRef(right); // TODO

        if (ctx.MUL() != null)
            return LLVMConstInt(i32Type, left_val * right_val, 0);
        if (ctx.DIV() != null)
            return LLVMConstInt(i32Type, left_val / right_val, 0);
        if (ctx.MOD() != null)
            return LLVMConstInt(i32Type, left_val % right_val, 0);
        return null;

    }

    @Override
    public LLVMValueRef visitUNARY_EXP(SysYParserParser.UNARY_EXPContext ctx) {
        LLVMValueRef exp = ctx.exp().accept(this);

        long val = getValueByRef(exp); // TODO

        if (ctx.unaryOp().PLUS() != null)
            return LLVMConstInt(i32Type, val, 0);
        if (ctx.unaryOp().MINUS() != null)
            return LLVMConstInt(i32Type, -val, 0);
        if (ctx.unaryOp().NOT() != null)
            return LLVMConstInt(i32Type, val == 0 ? 1 : 0, 0);
        return null;
    }


    @Override
    public LLVMValueRef visitEXP_PLUS_EXP(SysYParserParser.EXP_PLUS_EXPContext ctx) {
        LLVMValueRef left = ctx.exp(0).accept(this);
        LLVMValueRef right = ctx.exp(1).accept(this);

        long left_val = getValueByRef(left); // TODO
        long right_val = getValueByRef(right); // TODO

        if (ctx.PLUS() != null)
            return LLVMConstInt(i32Type, left_val + right_val, 0);
        if (ctx.MINUS() != null)
            return LLVMConstInt(i32Type, left_val - right_val, 0);
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

    public long getValueByRef(LLVMValueRef llvmValueRef) {
        if (LLVMGetTypeKind(LLVMTypeOf(llvmValueRef)) == LLVMIntegerTypeKind)
            return LLVMConstIntGetSExtValue(llvmValueRef);
        return Long.MAX_VALUE;
    }
}