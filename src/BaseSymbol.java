import org.antlr.v4.runtime.misc.Pair;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.ArrayList;

public class BaseSymbol implements Symbol{
    final String name;
    final Type type;

    LLVMValueRef ref;

    int arr;

    public ArrayList<Pair<Integer,Integer>> use = null;



    public BaseSymbol(String name, Type type) {
        this.name = name;
        this.ref = null;
        this.type = type;
    }

    public BaseSymbol(String name, LLVMValueRef ref) {
        this.name = name;
        this.ref = ref;
        this.type = null;
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

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public LLVMValueRef getRef() {return this.ref;}

    @Override
    public void setRef(LLVMValueRef llvmValueRef) {
        this.ref = llvmValueRef;
    }

    @Override
    public int getArr() {
        return arr;
    }

    @Override
    public void setArr(int a) {
        this.arr = a;
    }

}
