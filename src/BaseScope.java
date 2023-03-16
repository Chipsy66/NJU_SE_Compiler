import java.util.LinkedHashMap;
import java.util.Map;

public class BaseScope implements Scope {
    private final Scope enclosingScope;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private String name;

    public BaseScope(String name, Scope enclosingScope) {
        this.name = name;
        this.enclosingScope = enclosingScope;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Scope getEnclosingScope() {
        return this.enclosingScope;
    }

    public Map<String, Symbol> getSymbols() {
        return this.symbols;
    }

    @Override
    public boolean define(Symbol symbol) {
        if (symbols.get(symbol.getName())==null)
        {
            symbols.put(symbol.getName(), symbol);
            return true;
        }
        return false;

    }



    @Override
    public Symbol resolve(String name) {
        Symbol symbol = symbols.get(name);
        if (symbol != null) {
            return symbol;
        }
        if (enclosingScope != null) {
            return enclosingScope.resolve(name);
        }
        return null;
    }

    @Override
    public boolean checkSymbol(String name) {
        if (symbols.get(name)!=null)
            return true;
        if (enclosingScope != null)
            return enclosingScope.checkSymbol(name);
        return false;
    }
    @Override
    public boolean currHasSymbol(String name)
    {
        return symbols.get(name)!=null;
    }

    @Override
    public Type getType(String name) {
        if (symbols.get(name)!=null)
            return symbols.get(name).getType();
        if (enclosingScope != null)
        {
            return enclosingScope.getType(name);
        }

        return null;
    }

    @Override
    public Symbol getSymbol(String name) {
        if (symbols.get(name)!=null)
            return symbols.get(name);
        if (enclosingScope != null)
            return enclosingScope.getSymbol(name);
        return null;
    }


}
