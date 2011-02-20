package decaf
import static decaf.DecafParserTokenTypes.*

class HiIrBuilder {
  def methods = [:]
  Closure c = { AST cur ->
    switch (cur.getType()) {
    case TK_false:
      parent.walkerDelegate.children << new BooleanLiteral(value: false)
      break

    case TK_true:
      parent.walkerDelegate.children << new BooleanLiteral(value: true)
      break

    case INT_LITERAL:
      parent.walkerDelegate.children << new IntLiteral(value: cur.getText() as int)
      break

    case CHAR_LITERAL:
      def charData = cur.getText()
      if (charData.length() == 1) {
        parent.walkerDelegate.children << new IntLiteral(value: (int)charData[0])
      } else {
        assert charData[0] == '\\'
        def escMap = ['"': '"', '\'': '\'', '\\': '\\', 't': '\t', 'n': '\n']
        parent.walkerDelegate.children << new IntLiteral(value: escMap[charData[1]] as int)
      }
      break

    case PLUS_OP:
      parent.walkerDelegate.children << BinOpType.ADD
      break

    case REL_OP:
      parent.walkerDelegate.children << [
        '<':BinOpType.LT,
	'>':BinOpType.GT,
	'>=':BinOpType.GTE,
	'<=':BinOpType.LTE
      ][cur.getText()]
      break

    case MUL_DIV_OP:
      parent.walkerDelegate.children << [
        '*':BinOpType.MUL,
	'/':BinOpType.DIV,
	'%':BinOpType.MOD
      ][cur.getText()]
      break

    case EQ_OP:
      parent.walkerDelegate.children << [
        '==':BinOpType.EQ,
	'!=':BinOpType.NEQ
      ][cur.getText()]
      break

    case COND_AND:
      parent.walkerDelegate.children << BinOpType.AND
      break

    case COND_OR:
      parent.walkerDelegate.children << BinOpType.OR
      break
      
    case NOT_OP:
      declVar('children', [])
      walk()
      assert children.size() == 1      
      parent.walkerDelegate.children <<
        new BinOp(op:BinOpType.NOT, left:children[0])
      break
      
    case MINUS_OP:
      declVar('children', [])
      walk()
      if (children.size() == 0){
        parent.walkerDelegate.children << BinOpType.SUB
      } else {
        assert children.size() == 1
	parent.walkerDelegate.children <<
	  new BinOp(op:BinOpType.SUB, left:new IntLiteral(value:0), right:children[0])
      }
      break

    case FLAT_EXPR:
      declVar('children', [])
      walk()
      assert children.size() % 2 == 1
      while (children.size() != 1) {
        def left = children.remove(0)
        def op = children.remove(0)
        def right = children.remove(0)
        children.add(0, new BinOp(op: op, left: left, right: right))
      }
      parent.walkerDelegate.children << children[0]
      break

    case LOCATION:
      declVar('children',[])
      walk()
      assert children.size() == 1 || children.size() == 2
      //TODO: get the location's descriptor
      //probably like symtable[cur.getText()]
      parent.walkerDelegate.children << new Location(
        descriptor: children[0],
        indexExpr: children.size() == 2 ? children[1] : null
      )
      break

    case METHOD_CALL:
      declVar('children',[])
      walk()
      //TODO: fix descriptor
      parent.walkerDelegate.children << new MethodCall(
        descriptor: cur.getText(),
        params: children as List<Expr>
      )
      break

    case ID:
      parent.walkerDelegate.children << cur.getText()
      break

    case METHOD_DECL:
      declVar('children',[])
      walk()
      assert children.size() == 1
      methods[cur.getText()] = children[0]
      break

    case BLOCK:
      declVar('children',[])
      walk()
      //todo: get symbolTable
      parent.walkerDelegate.children << new Block(
        symbolTable: null,
        statements: children as List<Statement>
      )
      break

    case PLAIN_ASSIGN_OP:
    case MODIFY_ASSIGN_OP:
      parent.walkerDelegate.children << cur.getText()
      break

    //return or assignment, wtf david?
    case STATEMENT:
      declVar('children',[])
      walk()
      switch (cur.getText()) {

      case 'assignment':
        assert children.size == 3
        Location lvalue = children[0]
        Expr rvalue = children[2]
        if (children[1] == '+=')
          rvalue = new BinOp(op:BinOpType.ADD, right: lvalue, left: rvalue)
        else if (children[1] == '-=')
          rvalue = new BinOp(op:BinOpType.SUB, right: lvalue, left: rvalue)
        parent.walkerDelegate.children << new Assignment(loc: lvalue, expr: rvalue)
        break

      case 'return':
        assert children.size() <= 1
        def expr = null
        if (children.size() == 1)
          expr = children[0]
        parent.walkerDelegate.children << new Return(expr: expr)
        break
      }
      break

    case TK_if:
      declVar('children',[])
      walk()
      assert children.size() == 2 || children.size() == 3
      def ifStmt = new IfThenElse()
      ifStmt.condition = children[0]
      ifStmt.thenBlock = children[1]
      if (children.size() == 3)
        ifStmt.elseBlock = children[2]
      parent.walkerDelegate.children << ifStmt
      break

    case TK_for:
      declVar('children',[])
      walk()
      assert children.size() == 4
      parent.walkerDelegate.children << new ForLoop(
        index: new Location(descriptor: children[0]),
        low: children[1],
        high: children[2],
        block: children[3]
      )
      break

    case TK_break:
      parent.walkerDelegate.children << new Break()
      break

    case TK_continue:
      parent.walkerDelegate.children << new Continue()
      break

    case STRING_LITERAL:
      parent.walkerDelegate.children << cur.getText()
      break

    case TK_callout:
      declVar('children',[])
      walk()
      assert children.size() >= 1
      children.each {
        assert it instanceof String || it instanceof Expr
      }
      parent.walkerDelegate.children << new CallOut(
        name: children[0],
        params: children.subList(1, children.size())
      )
      break

    case PROGRAM:
      walk()
    case VAR_DECL:
    case TK_int:
    case TK_void:
    case TK_boolean:
      break

    default:
      assert false
      break
    }
  }
}
