grammar SysYLexer;


//*:0 or more
prog :  .* EOF ;

CONST : 'const';

INT : 'int';

VOID : 'void';

IF : 'if';

ELSE : 'else';

WHILE : 'while';

BREAK : 'break';

CONTINUE : 'continue';

RETURN : 'return';

PLUS : '+';

MINUS : '-';

MUL : '*';

DIV : '/';

MOD : '%';

ASSIGN : '=';

EQ : '==';

NEQ : '!=';

LT : '<';

GT : '>';

LE : '<=';

GE : '>=';

NOT : '!';

AND : '&&';

OR : '||';

L_PAREN : '(';

R_PAREN : ')';

L_BRACE : '{';

R_BRACE : '}';

L_BRACKT : '[';

R_BRACKT : ']';

COMMA : ',';

SEMICOLON : ';';

IDENT :
    (LETTER|'_') (LETTER|DIGIT|'_')*
   ;

INTEGR_CONST :
    DECIMAL | OCTAL | HEX
   ;

WS
   : [ \r\n\t]+ -> skip
   ;

LINE_COMMENT
   : '//' .*? '\n' -> skip
   ;

MULTILINE_COMMENT
   : '/*' .*? '*/' -> skip
   ;


fragment DECIMAL :
    '0'|(NONZERO DIGIT*);

fragment OCTAL :
    '0' OCTALDIGIT+;

fragment HEX :
    HEXPRE HEXDIGIT+;

fragment LETTER : [a-zA-Z] ;
fragment DIGIT : [0-9] ;
fragment NONZERO : [1-9] ;
fragment OCTALDIGIT : [0-7] ;
fragment HEXPRE : '0x'|'0X';
fragment HEXDIGIT : [0-9a-fA-F];