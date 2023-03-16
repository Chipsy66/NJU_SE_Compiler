grammar SysYParser;

options {tokenVocab = SysYLexer;}

//*:0 or more
program
   : compUnit
   ;
compUnit
   : (funcDef | decl)+ EOF
   ;

decl : constDecl | varDecl;

constDecl : CONST bType constDef (COMMA constDef)* SEMICOLON ;

bType : INT ;

constDef : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal ;

constInitVal : constExp
                | L_BRACE (constInitVal ( COMMA constInitVal )*)? R_BRACE ;

varDecl : bType varDef (COMMA varDef)* SEMICOLON ;

varDef : IDENT (L_BRACKT constExp R_BRACKT)*    #VARDEF_NO_INIT
        | IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN initVal    #VARDEF_INIT
        ;

initVal : exp | L_BRACE (initVal (COMMA initVal)*)? R_BRACE ;

funcDef : funcType IDENT L_PAREN (funcFParams)? R_PAREN block ;

funcType : VOID | INT ;

funcFParams : funcFParam (COMMA funcFParam)* ;

funcFParam : bType IDENT (L_BRACKT R_BRACKT (L_BRACKT exp R_BRACKT)*)? ;

block : L_BRACE (blockItem)* R_BRACE ;

blockItem : decl | stmt ;

stmt :  lVal ASSIGN exp SEMICOLON #STMT_ASSIGN
        | (exp)? SEMICOLON  #STMT_EXP
        | block                 #STMT_BLOCK
        | IF L_PAREN cond R_PAREN stmt (ELSE stmt)? #STMT_IF
        | WHILE L_PAREN cond R_PAREN stmt   #STMT_WHILE
        | BREAK SEMICOLON #STMT_BREAK
        | CONTINUE SEMICOLON  #STMT_CONTINUE
        | RETURN (exp)? SEMICOLON #STMT_RETURN
        ;

exp  : L_PAREN exp R_PAREN #L_EXP_R
          | lVal   #EXP_LVAL
          | number #EXP_NUM
          | IDENT L_PAREN funcRParams? R_PAREN     #EXP_FUNC_ARGUMENT
          | unaryOp exp # UNARY_EXP
          | exp (MUL | DIV | MOD) exp #EXP_MUL_EXP
          | exp (PLUS | MINUS) exp  #EXP_PLUS_EXP
          ;

cond : exp  #COND_EXP
          | cond (LT | GT | LE | GE) cond   #COND_LT
          | cond (EQ | NEQ) cond    #COND_EQ
          | cond AND cond   #COND_AND
          | cond OR cond    #COND_OR
          ;

lVal : IDENT (L_BRACKT exp R_BRACKT)* ;

primaryExp : L_PAREN exp R_PAREN | lVal | number ;

number : INTEGR_CONST ;

unaryExp : primaryExp | IDENT L_PAREN (funcRParams)? R_PAREN
            | unaryOp unaryExp ;

unaryOp : PLUS | MINUS | NOT ;

funcRParams
   : param (COMMA param)*
   ;

param
   : exp
   ;

constExp : exp ;

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
   : '//' .*? '\n'
   ;

MULTILINE_COMMENT
   : '/*' .*? '*/'
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