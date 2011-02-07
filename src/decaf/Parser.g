header {package decaf;}

options
{
  mangleLiteralPrefix = "TK_";
  language="Java";
}

class DecafParser extends Parser;
options
{
  importVocab=DecafScanner;
  k=3;
  buildAST=true;
}

program: TK_class TK_Program ID LCURLY field_decl* method_decl* RCURLY EOF;

field_decl: type field_var (COMMA field_var)* SEMICOLON;

field_var: (ID | (ID LSQUARE INT_LITERAL RSQUARE));

method_decl: (type | TK_void) ID LPAREN method_arg (COMMA method_arg)* RPAREN block;

method_arg: type ID;

block: LCURLY var_decl* statement* RCURLY;

var_decl: type ID (COMMA ID)* SEMICOLON;

type: (TK_int | TK_boolean);

statement: (location assign_op expr SEMICOLON) |
           (method_call) |
           (TK_if LPAREN expr RPAREN block (TK_else block)?) |
           (TK_for ID '=' expr COMMA expr block) |
           (TK_return expr?) |
           (TK_break SEMICOLON) |
           (TK_continue SEMICOLON) |
           block;
           
method_call: (method_name LPAREN expr (COMMA expr)* RPAREN) |
             (TK_callout LPAREN string_literal (COMMA callout_arg (COMMA callout_arg)*));
             
method_name: ID;

location: ID | (ID LSQUARE expr RSQUARE);

expr: location | method_call | literal | (expr bin_op expr) | ('-' expr) | ('!' expr) | (LPAREN expr RPAREN);

callout_arg: expr | string_literal;

bin_op: arith_op | rel_op | eq_op | cond_op;