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
  defaultErrorHandler=false;
  k=3;
  buildAST=true;
}

program: TK_class^ TK_Program^ LCURLY (field_decl)* (method_decl)* RCURLY EOF;

field_decl: type field_var (COMMA field_var)* SEMICOLON;

field_var: (ID | (ID LSQUARE INT_LITERAL RSQUARE));

method_decl: (type | TK_void) ID LPAREN (method_arg (COMMA method_arg)*)? RPAREN block;

method_arg: type ID;

var_decl: type ID (COMMA ID)* SEMICOLON;

block: LCURLY (var_decl)* (statement)* RCURLY;

type: (TK_int^ | TK_boolean^);

statement: (location assign_op expr SEMICOLON) |
           (method_call SEMICOLON) |
           (TK_if LPAREN expr RPAREN block (TK_else block)?) |
           (TK_for ID PLAIN_ASSIGN_OP^ expr COMMA expr block) |
           (TK_return (expr)? SEMICOLON) |
           (TK_break SEMICOLON) |
           (TK_continue SEMICOLON) |
           block;
           
method_call: (method_name LPAREN (expr (COMMA expr)*)? RPAREN) |
             (TK_callout LPAREN STRING_LITERAL (COMMA callout_arg (COMMA callout_arg)*)? RPAREN);
             
method_name: ID;

location: ID | (ID LSQUARE expr RSQUARE);

expr: cond_or_expr;

cond_or_expr: cond_and_expr cond_or_expr_dash;
cond_or_expr_dash: COND_OR^ cond_and_expr cond_or_expr_dash | /* empty */;

cond_and_expr: equality_expr cond_and_expr_dash;
cond_and_expr_dash: COND_AND^ equality_expr cond_and_expr_dash | /* empty */;

equality_expr: relational_expr equality_expr_dash;
equality_expr_dash: EQ_OP^ relational_expr equality_expr_dash | /* empty */;

relational_expr: add_sub_expr relational_expr_dash;
relational_expr_dash: REL_OP^ add_sub_expr | /* empty */;

add_sub_expr: mul_div_expr add_sub_expr_dash;
add_sub_expr_dash: (PLUS_OP^ | MINUS_OP^) mul_div_expr add_sub_expr_dash | /* empty */;

mul_div_expr: logical_not_expr mul_div_expr_dash;
mul_div_expr_dash: MUL_DIV_OP^ logical_not_expr mul_div_expr_dash | /* empty */;

logical_not_expr: NOT_OP^ logical_not_expr | unary_minus_expr;
unary_minus_expr: MINUS_OP^ unary_minus_expr | precedence_expr;
precedence_expr: LPAREN expr RPAREN | literal | location | method_call;

literal: INT_LITERAL | CHAR_LITERAL | BOOL_LITERAL;

callout_arg: expr | STRING_LITERAL;

bin_op: arith_op | REL_OP | EQ_OP | cond_op;

arith_op: PLUS_OP | MINUS_OP | MUL_DIV_OP;

cond_op: COND_AND | COND_OR;

assign_op: PLAIN_ASSIGN_OP^ | MODIFY_ASSIGN_OP^;