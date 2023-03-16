import org.antlr.v4.runtime.misc.Pair;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.ArrayList;

public class FunctionSymbolScope extends BaseScope implements Symbol, Type{

    public Type returnType;
    public ArrayList<Type> paramsTypes;

    public ArrayList<String> params_Types_name = new ArrayList<>();

    public ArrayList<Pair<Integer,Integer>> use = null;

    public LLVMValueRef ref;

    public String returnT;
    public boolean isFalse;

    public FunctionSymbolScope(String name, Scope enclosingScope) {
        super(name, enclosingScope);
        paramsTypes = new ArrayList<>();

    }

    @Override
    public void addUse(int line, int col) {
        if (use==null)
            use = new ArrayList<>();
        use.add(new Pair<>(line,col));
    }

    @Override
    public ArrayList<Pair<Integer, Integer>> getUse() {
        return use;
    }

    @Override
    public Type getType() {
        return null;
    }

    @Override
    public LLVMValueRef getRef() {
        return ref;
    }

    public void setRef(LLVMValueRef ref) {
        this.ref = ref;
    }

    @Override
    public int getArr() {
        return 0;
    }

    @Override
    public void setArr(int a) {

    }

}
