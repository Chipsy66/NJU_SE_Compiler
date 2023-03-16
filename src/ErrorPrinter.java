public class ErrorPrinter {

    public static void print(int type, int line, String suffix)
    {
        Main.error = 1;
        System.err.println("Error type "+type+" at Line "+line+suffix);
    }

    public static void print(int type, int line)
    {
        print(type, line, "");
    }
}
