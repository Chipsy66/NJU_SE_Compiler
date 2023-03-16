import org.antlr.v4.runtime.misc.Pair;
import org.bytedeco.llvm.LLVM.LLVMValueRef;

import java.util.ArrayList;

public interface Symbol {


    public void addUse(int line, int col);

    public ArrayList<Pair<Integer,Integer>> getUse();

    public String getName();

    public Type getType();

    public LLVMValueRef getRef();

    public void setRef(LLVMValueRef llvmValueRef);

    public int getArr();

    public void setArr(int a);
}
