header {package decaf;}

options 
{
  mangleLiteralPrefix = "TK_";
  language="Java";
}

class DecafScanner extends Lexer;
options 
{
  testLiterals = false;
  k=2;
}

tokens 
{
  "boolean";
  "break";
  "callout";
  "class";
  "continue";
  "else";
  "false";
  "for";
  "if";
  "int";
  "return";
  "true";
  "void";
}

LCURLY options { paraphrase = "{"; } : "{";
RCURLY options { paraphrase = "}"; } : "}";
SEMICOLON options { paraphrase = ";"; } : ';';
COMMA options { paraphrase = ","; } : ',';
LSQUARE options { paraphrase = "["; } : "[";
RSQUARE options { paraphrase = "]"; } : "]";
LPAREN options { paraphrase = "("; } : "(";
RPAREN options { paraphrase = ")"; } : ")";

protected ALPHA :
  ('a'..'z' | 'A'..'Z' | '_');

protected DIGIT :
  '0'..'9';
  
protected ALPHA_NUM :
  (ALPHA | DIGIT);
  
protected HEX_DIGIT :
  (DIGIT | 'a'..'f' | 'A'..'F');
  
protected DECIMAL_LITERAL :
  (DIGIT)+;
  
protected HEX_LITERAL :
  "0x" (HEX_DIGIT)+;
  
INT_LITERAL options { paraphrase = "an int literal"; } :
  DECIMAL_LITERAL | HEX_LITERAL;

CHAR_LITERAL options { paraphrase = "a char literal"; } :
  '\'' CHAR '\'';
 
STRING_LITERAL options { paraphrase = "a string literal"; } :
  '"' (CHAR)* '"';

ID options { paraphrase = "an identifier"; testLiterals = true; } : 
  ALPHA (ALPHA_NUM)*;

ARITH_OP : ( {LA(2) != '='}? ('+' | '-') | '*' | '/' | '%');

REL_OP : ('<' | '>' | "<=" | ">=");

EQ_OP : ( {LA(2) == '='}? "==" | "!=");

COND_OP : ("&&" | "||");

ASSIGN_OP : ( {LA(2) != '='}? '=' | {LA(2) == '='}? ("+=" | "-="));

protected CHAR : (' '..'!' | '#'..'&' | '('..'[' | ']'..'~' | ESC_COMMON);

protected ESC_COMMON : '\\' ('"' | '\'' | '\\' | 't' | 'n');

WS_ : (' ' | '\t' | '\n' {newline();}) {_ttype = Token.SKIP; };

SL_COMMENT : "//" (~'\n')* '\n' {_ttype = Token.SKIP; newline (); };