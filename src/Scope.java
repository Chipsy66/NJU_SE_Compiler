import java.util.Map;

public interface Scope {
    public String getName();

    public void setName(String name);

    public Scope getEnclosingScope();

    public Map<String, Symbol> getSymbols();

    public boolean define(Symbol symbol);

    public Symbol resolve(String name);

    public boolean checkSymbol(String name);

    public Type getType(String name);

    public Symbol getSymbol(String name);

    public boolean currHasSymbol(String name);
}
