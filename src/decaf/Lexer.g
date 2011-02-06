header {package decaf;}

options 
{
  mangleLiteralPrefix = "TK_";
  language="Java";
}

class DecafScanner extends Lexer;
options 
{
  k=2;
}

tokens 
{
  "class";
}

LCURLY options { paraphrase = "{"; } : "{";
RCURLY options { paraphrase = "}"; } : "}";

ID options { paraphrase = "an identifier"; } : 
  ('a'..'z' | 'A'..'Z')+;

WS_ : (' ' | '\n' {newline();}) {_ttype = Token.SKIP; };

SL_COMMENT : "//" (~'\n')* '\n' {_ttype = Token.SKIP; newline (); };

CHAR : '\'' (ESC|~'\'') '\'';
STRING : '"' (ESC|~'"')* '"';

protected
ESC :  '\\' ('n'|'"');
