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

tokens {
  PROGRAM; //everything under this node
  
  METHOD_DECL; //one decl per method
  VAR_DECL; //each arg has type and ID under this
  ARRAY_DECL; //one per array, has the name in the text and the size as a child
  
  BLOCK;
  STATEMENT; //one per statement
  LOCATION;
  METHOD_CALL; //one per method, text is method name, args are children
  
  FLAT_EXPR;
 }

program!: TK_class TK_Program LCURLY a:field_decls b:method_decls RCURLY EOF {#program = #([PROGRAM,"Program"],a,b);};

method_decls: (method_decl)*;

field_decls: (field_decl)*;

//put fields into a field token giving the overall type
field_decl: t:type vs:field_vars SEMICOLON! {#field_decl = #([VAR_DECL,t_AST.getText()],vs);};

field_vars: field_var (COMMA! field_var)*;

//put arrays into an array token w/ size as a child
field_var: (ID 
           | (! name:ID LSQUARE i:INT_LITERAL RSQUARE) {#field_var = #([ARRAY_DECL,#name.getText()],i);});

//TODO: add return type to parse tree
method_decl!: (type | TK_void) name:ID LPAREN (args:method_arg_decls)? RPAREN block:block
              {#method_decl = #([METHOD_DECL,name.getText()],args,block);};

method_arg_decls: method_arg_decl (COMMA! method_arg_decl)*;
method_arg_decl!: t:type id:ID {#method_arg_decl = #([VAR_DECL,t_AST.getText()],id);};

var_decl!: t:type names:var_decl_names SEMICOLON {#var_decl = #([VAR_DECL,t_AST.getText()],names);};
var_decl_names: ID (COMMA! ID)*;

block!: LCURLY (a:var_decl)* b:statements RCURLY {#block = #([BLOCK,"block"],a,b);};

type: (TK_int | TK_boolean);

statements: (statement)*;
statement: a:assignment SEMICOLON! {#statement = #([STATEMENT,"assignment"],a);} |
           (method_call SEMICOLON!) |
           (TK_if^ LPAREN! expr RPAREN! block (TK_else! block)?) |
           (TK_for^ ID PLAIN_ASSIGN_OP! expr COMMA! expr block) |
           (TK_return (expr:expr)? SEMICOLON!) {#statement = #([STATEMENT,"return"],expr);}|
           (TK_break SEMICOLON!) |
           (TK_continue SEMICOLON!) |
           block;

assignment: l:location op:assign_op expr:expr;

method_call!: (name:ID LPAREN (args:method_args)? RPAREN) {#method_call = #([METHOD_CALL,#name.getText()],args);}
              | (TK_callout LPAREN STRING_LITERAL (COMMA callout_arg (COMMA callout_arg)*)? RPAREN);
method_args: expr (COMMA! expr)*;

location!: id:ID {#location = #([LOCATION,"loc: ID"],id);}
           | (a:ID LSQUARE b:expr RSQUARE) {#location = #([LOCATION,"loc: subscript"],a,b);};

expr: cond_or_expr;

cond_or_expr!: a:cond_and_expr b:cond_or_expr_dash {#cond_or_expr = #b != null ? #([FLAT_EXPR,"|| expr"],a,b) : #a;};
cond_or_expr_dash: COND_OR cond_and_expr cond_or_expr_dash | /* empty */;

cond_and_expr!: a:equality_expr b:cond_and_expr_dash {#cond_and_expr = #b != null ? #([FLAT_EXPR,"&& expr"],a,b) : #a;};
cond_and_expr_dash: COND_AND equality_expr cond_and_expr_dash | /* empty */;

equality_expr!: a:relational_expr b:equality_expr_dash {#equality_expr = #b != null ? #([FLAT_EXPR,"equality expr"],a,b) : #a;};
equality_expr_dash: EQ_OP relational_expr equality_expr_dash | /* empty */;

relational_expr!: a:add_sub_expr b:relational_expr_dash {#relational_expr = #b != null ? #([FLAT_EXPR,"relational expr"],a,b) : #a;};
relational_expr_dash: REL_OP add_sub_expr | /* empty */;

add_sub_expr!: a:mul_div_expr b:add_sub_expr_dash {#add_sub_expr = #b != null ? #([FLAT_EXPR,"add sub expr"],a,b) : #a;};
add_sub_expr_dash: plus_minus_op mul_div_expr add_sub_expr_dash | /* empty */;

mul_div_expr!: a:unary_expr b:mul_div_expr_dash {#mul_div_expr = #b != null ? #([FLAT_EXPR,"mul div expr"],a,b) : #a;};
mul_div_expr_dash: MUL_DIV_OP unary_expr mul_div_expr_dash | /* empty */;

//TODO: confirm this is a sane change
unary_expr: NOT_OP^ unary_expr | MINUS_OP^ unary_expr | precedence_expr;
//logical_not_expr: NOT_OP^ logical_not_expr | unary_minus_expr;
//unary_minus_expr: MINUS_OP^ unary_minus_expr | precedence_expr;
precedence_expr: LPAREN! expr RPAREN! | literal | location | method_call;

literal: INT_LITERAL | CHAR_LITERAL | BOOL_LITERAL;

callout_arg: expr | STRING_LITERAL;

bin_op: arith_op | REL_OP | EQ_OP | cond_op;

plus_minus_op: PLUS_OP | MINUS_OP;

arith_op: plus_minus_op | MUL_DIV_OP;

cond_op: COND_AND | COND_OR;

assign_op: PLAIN_ASSIGN_OP | MODIFY_ASSIGN_OP					;