import org.bytedeco.llvm.LLVM.LLVMValueRef;

public class BasicTypeSymbol extends BaseSymbol implements Type{
    public BasicTypeSymbol(String name) {
        super(name, (LLVMValueRef) null);
    }
    @Override
    public String toString() {
        return name;
    }
}
