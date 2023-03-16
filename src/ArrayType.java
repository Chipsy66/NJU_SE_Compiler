public class ArrayType implements Type{
    public Type elementType;
    public int elementCount;

    public int layer;

    ArrayType(int layer ){
        this.layer = layer;
    }

}
