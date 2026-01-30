
import java.util.*;

/*
    Analizador Léxico (Tokenizer) SIMPLE para un subconjunto de Java.
    - NO usa regex
    - NO usa autómatas formales
    - Recorre el texto carácter por carácter con punteros (index), peek(), advance()

    Produce:
    1) Lista secuencial de tokens con: tipo, lexema, línea, columna
    2) Tabla de símbolos (identificadores) con conteo de apariciones
*/
public class Tokenizer {

    public static void main(String[] args) {

  
        String code = """
            public class PotionBrewer {
                // Ingredient costs in gold coins
                private static final double HERB_PRICE = 5.50;
                private static final int MUSHROOM_PRICE = 3;
                private String brewerName;
                private double goldCoins;
                private int potionsBrewed;

                public PotionBrewer(String name, double startingGold) {
                    this.brewerName = name;
                    this.goldCoins = startingGold;
                    this.potionsBrewed = 0;
                }

                public static void main(String[] args) {
                    PotionBrewer wizard = new PotionBrewer("Gandalf, the Wise", 100.0);
                    String[] ingredients = {"Mandrake Root", "Dragon Scale", "Phoenix Feather"};

                    wizard.brewHealthPotion(3, 2); // 3 herbs, 2 mushrooms
                    wizard.brewHealthPotion(5, 4);

                    wizard.printStatus();
                }

                /* Brews a potion if we have enough gold */
                public void brewHealthPotion(int herbCount, int mushroomCount) {
                    double totalCost = (herbCount * HERB_PRICE) + (mushroomCount * MUSHROOM_PRICE);
                    if (totalCost <= this.goldCoins) {
                        this.goldCoins -= totalCost; // Deduct the cost
                        this.potionsBrewed++;
                        System.out.println("Success! Potion brewed for " + totalCost + " gold.");
                    } else {
                        System.out.println("Not enough gold! Need: " + totalCost);
                    }
                }

                // Prints the current brewer status
                public void printStatus() {
                    System.out.println("\\n=== Brewer Status ===");
                    System.out.println("Name: " + this.brewerName);
                    System.out.println("Gold remaining: " + this.goldCoins);
                    System.out.println("Potions brewed: " + this.potionsBrewed);
                }
            }
            """;

        Lexer lexer = new Lexer(code);
        List<Token> tokens = lexer.tokenize();

        // 1) Imprimir tokens en orden
        System.out.println("=== Tokens ===");
        for (Token t : tokens) {
            System.out.println(t);
        }

        // 2) Imprimir tabla de símbolos
        System.out.println();
        lexer.getSymbolTable().print();
    }
}

/* =========================
   1) Tipos de Token
   ========================= */
enum TokenType {
    KEYWORD,        // public, class, if, else, etc.
    IDENTIFIER,     // nombres: variables, clases, métodos
    NUMBER,         // 3, 5.50, 100.0
    STRING,         // "texto"
    OPERATOR,       // =, +, -, *, <=, ++, -=, etc.
    DELIMITER,      // ( ) { } [ ] ; , .
    COMMENT,        // //... o /*...*/ (aquí los ignoraremos por defecto)
    EOF,            // fin de archivo
    ERROR           // símbolo inválido o string sin cerrar, etc.
}

/* =========================
   2) Token: tipo + lexema + posición
   ========================= */
class Token {
    TokenType type;
    String lexeme;
    int line;
    int column;

    Token(TokenType type, String lexeme, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return "[" + line + ":" + column + "] " + type + " -> \"" + lexeme + "\"";
    }
}

/* =========================
   3) Tabla de símbolos (identificadores)
   - Guardamos cada IDENTIFIER y cuántas veces aparece.
   ========================= */
class SymbolTable {
    // LinkedHashMap mantiene el orden de inserción (útil para imprimir ordenado por aparición)
    private final Map<String, Integer> table = new LinkedHashMap<>();

    public void add(String identifier) {
        table.put(identifier, table.getOrDefault(identifier, 0) + 1);
    }

    public void print() {
        System.out.println("=== Symbol Table (Identifiers) ===");
        for (var entry : table.entrySet()) {
            System.out.println(entry.getKey() + "  (count=" + entry.getValue() + ")");
        }
    }
}

/* =========================
   4) Lexer / Scanner
   - Recorre el código carácter por carácter.
   - Decide qué token empieza en el char actual.
   ========================= */
class Lexer {

    private final String source;
    private int index = 0;   // posición absoluta en el string
    private int line = 1;    // línea actual (empieza en 1)
    private int column = 1;  // columna actual (empieza en 1)

    private final List<Token> tokens = new ArrayList<>();
    private final SymbolTable symbolTable = new SymbolTable();

    /*
        Keywords: conjunto de palabras reservadas.
        - Si leemos un identificador y está aquí, lo clasificamos como KEYWORD.
        - Si no está, es IDENTIFIER.
    */
    private static final Set<String> KEYWORDS = Set.of(
            "public", "class", "private", "static", "final",
            "int", "double", "void", "if", "else", "new", "return",
            "String"
    );

    // Operadores de 2 chars: siempre intentamos matchear estos primero (ej: <=, ++)
    private static final Set<String> TWO_CHAR_OPS = Set.of(
            "<=", ">=", "==", "!=", "++", "--", "+=", "-=", "*=", "/=", "&&", "||"
    );

    // Operadores de 1 char
    private static final Set<Character> ONE_CHAR_OPS = Set.of(
            '=', '+', '-', '*', '/', '<', '>', '!', '%'
    );

    // Delimitadores / puntuación
    private static final Set<Character> DELIMITERS = Set.of(
            '(', ')', '{', '}', '[', ']', ';', ',', '.'
    );

    Lexer(String source) {
        this.source = source;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }

    /*
        tokenize():
        - Loop principal: mientras no se acabe el texto, escanea un token a la vez.
        - Al final, agrega EOF.
    */
    public List<Token> tokenize() {
        while (!isAtEnd()) {
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    /* =========================
       Helpers de navegación
       ========================= */

    private boolean isAtEnd() {
        return index >= source.length();
    }

    /*
        peek():
        - Mira el carácter actual SIN avanzar.
        - Si estamos al final, devuelve '\0'.
    */
    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(index);
    }

    /*
        peekNext():
        - Mira el siguiente char sin avanzar.
    */
    private char peekNext() {
        return (index + 1 >= source.length()) ? '\0' : source.charAt(index + 1);
    }

    /*
        advance():
        - Devuelve el char actual y AVANZA index.
        - Actualiza line/column.
    */
    private char advance() {
        char c = source.charAt(index++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }

    /*
        match(expected):
        - Si el char actual es expected, lo consume y devuelve true.
        - Si no, no consume y devuelve false.
        - Sirve para operadores de 2 chars: <=, ++, etc.
    */
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        if (source.charAt(index) != expected) return false;
        // consumimos el expected
        index++;
        column++;
        return true;
    }

    /*
        addToken(type, lexeme, startLine, startCol):
        - Crea y agrega un token con la posición donde empezó el lexema.
    */
    private void addToken(TokenType type, String lexeme, int startLine, int startCol) {
        tokens.add(new Token(type, lexeme, startLine, startCol));
    }

    /* =========================
       5) scanToken(): decide qué leer
       ========================= */
    private void scanToken() {
        // Guardamos la posición inicial del token (antes de consumir chars)
        int startLine = line;
        int startCol = column;

        char c = peek();

        /* 1) Ignorar whitespace */
        if (c == ' ' || c == '\t' || c == '\r') {
            advance();
            return;
        }
        if (c == '\n') {
            advance(); // advance ya actualiza line/col
            return;
        }

        /* 2) Comentarios: //... o /*...*\/ */
        if (c == '/' && peekNext() == '/') {
            consumeLineComment(); // por defecto: ignoramos comentario
            return;
        }
        if (c == '/' && peekNext() == '*') {
            consumeBlockComment(); // por defecto: ignoramos comentario
            return;
        }

        /* 3) Strings: " ... " */
        if (c == '"') {
            scanString(startLine, startCol);
            return;
        }

        /* 4) Delimitadores */
        if (DELIMITERS.contains(c)) {
            advance();
            addToken(TokenType.DELIMITER, String.valueOf(c), startLine, startCol);
            return;
        }

        /* 5) Operadores (2 chars primero) */
        String two = "" + c + peekNext();
        if (TWO_CHAR_OPS.contains(two)) {
            advance(); // consume c
            advance(); // consume next
            addToken(TokenType.OPERATOR, two, startLine, startCol);
            return;
        }
        if (ONE_CHAR_OPS.contains(c)) {
            advance();
            addToken(TokenType.OPERATOR, String.valueOf(c), startLine, startCol);
            return;
        }

        /* 6) Números */
        if (isDigit(c)) {
            scanNumber(startLine, startCol);
            return;
        }

        /* 7) Identificadores / Keywords */
        if (isAlpha(c)) {
            scanIdentifierOrKeyword(startLine, startCol);
            return;
        }

        /* 8) Si no cae en nada: ERROR */
        advance();
        addToken(TokenType.ERROR, String.valueOf(c), startLine, startCol);
    }

    /* =========================
       6) Scanners específicos
       ========================= */

    /*
        scanIdentifierOrKeyword:
        - Un identificador empieza con letra o '_' o '$'
        - Continúa con letras, dígitos, '_' o '$'
        - Luego verificamos si está en KEYWORDS
    */
    private void scanIdentifierOrKeyword(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();

        // Consumimos mientras sea alfanumérico válido para identificadores
        while (isAlphaNumeric(peek())) {
            sb.append(advance());
        }

        String lexeme = sb.toString();

        if (KEYWORDS.contains(lexeme)) {
            addToken(TokenType.KEYWORD, lexeme, startLine, startCol);
        } else {
            addToken(TokenType.IDENTIFIER, lexeme, startLine, startCol);
            symbolTable.add(lexeme); // tabla de símbolos SOLO para IDENTIFIER
        }
    }

    /*
        scanNumber:
        - Consume dígitos
        - Si ve '.' y luego dígito, consume parte decimal (double)
    */
    private void scanNumber(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();

        while (isDigit(peek())) {
            sb.append(advance());
        }

        // Parte decimal: '.' seguido de dígito
        if (peek() == '.' && isDigit(peekNext())) {
            sb.append(advance()); // consume '.'
            while (isDigit(peek())) {
                sb.append(advance());
            }
        }

        addToken(TokenType.NUMBER, sb.toString(), startLine, startCol);
    }

    /*
        scanString:
        - Consume la comilla inicial
        - Lee hasta la próxima comilla (maneja saltos de línea)
        - Si llega al EOF sin cerrar, emite ERROR
    */
    private void scanString(int startLine, int startCol) {
        advance(); // consume la comilla inicial "

        StringBuilder sb = new StringBuilder();

        while (!isAtEnd() && peek() != '"') {
            // Si hay salto de línea dentro de string, advance() ya actualiza line/col
            
            sb.append(advance());
        }

        if (isAtEnd()) {
            // String sin cerrar: recuperación simple -> reportamos error y seguimos
            addToken(TokenType.ERROR, "Unterminated string", startLine, startCol);
            return;
        }

        advance(); // consume comilla final "
        addToken(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    /*
        consumeLineComment:
        - Consume "//" y luego todo hasta '\n'
        - Por defecto, NO genera token (lo ignora).
    
    */
    private void consumeLineComment() {
        // consume "//"
        advance();
        advance();

        while (!isAtEnd() && peek() != '\n') {
            advance();
        }
        
    }

  
   
    
    private void consumeBlockComment() {
        // consume "/*"
        advance();
        advance();

        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance(); // *
                advance(); // /
                return;
            }
            advance();
        }

        // Si llegamos aquí, no se cerró el comentario
        addToken(TokenType.ERROR, "Unterminated block comment", line, column);
    }

    /* =========================
       7) Funciones de clase de caracteres
       ========================= */

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /*
        isAlpha:
        - Para soportar Unicode (p.ej. japonés), usamos Character.isLetter(c)
        - También permitimos '_' y '$' como en Java.
    */
    private boolean isAlpha(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}
