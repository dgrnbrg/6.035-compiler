package decaf
import static decaf.DecafParserTokenTypes.*

class HiIrGenerator {
  def errors

  def methods = [:]

  def methodSymTable

  def inductionVars = []

  Closure c = { AST cur ->
    declVar('children',[])
    def symTable = cur.walkerDelegate.@properties['symTable']
    if (symTable == null) {
      symTable = cur.parent.symTable
      declVar('symTable', cur.parent.symTable)
    }
    def methodSymTableTmp
    if (methodSymTable == null && (methodSymTableTmp = cur.walkerDelegate.@properties['methodSymTable']) != null) {
      methodSymTable = methodSymTableTmp
    }

    walk()
    switch (cur.getType()) {
    case TK_false:
      parent.children << new BooleanLiteral(value: false, fileInfo: cur.fileInfo)
      break

    case TK_true:
      parent.children << new BooleanLiteral(value: true, fileInfo: cur.fileInfo)
      break

    case INT_LITERAL:
      def intLiteralVal
      if (cur.getText().startsWith('0x'))
        intLiteralVal = Long.parseLong(cur.getText().substring(2), 16)
      else
        intLiteralVal = Long.parseLong(cur.getText())
      parent.children << new IntLiteral(value: intLiteralVal, fileInfo: cur.fileInfo)
      break

    case CHAR_LITERAL:
      def charData = cur.getText()
      if (charData.length() == 1) {
        parent.children << new IntLiteral(value: (int)charData[0], fileInfo: cur.fileInfo)
      } else {
        assert charData[0] == '\\'
        def escMap = ['"': '"', '\'': '\'', '\\': '\\', 't': '\t', 'n': '\n']
        parent.children << new IntLiteral(value: escMap[charData[1]] as int, fileInfo: cur.fileInfo)
      }
      break

    case PLUS_OP:
      parent.children << BinOpType.ADD
      break

    case REL_OP:
      parent.children << [
        '<':BinOpType.LT,
	'>':BinOpType.GT,
	'>=':BinOpType.GTE,
	'<=':BinOpType.LTE
      ][cur.getText()]
      break

    case MUL_DIV_OP:
      parent.children << [
        '*':BinOpType.MUL,
	'/':BinOpType.DIV,
	'%':BinOpType.MOD
      ][cur.getText()]
      break

    case EQ_OP:
      parent.children << [
        '==':BinOpType.EQ,
	'!=':BinOpType.NEQ
      ][cur.getText()]
      break

    case COND_AND:
      parent.children << BinOpType.AND
      break

    case COND_OR:
      parent.children << BinOpType.OR
      break
      
    case NOT_OP:
      assert children.size() == 1      
      parent.children <<
        new BinOp(op:BinOpType.NOT, left:children[0], fileInfo: cur.fileInfo)
      break
      
    case MINUS_OP:
      if (children.size() == 0){
        parent.children << BinOpType.SUB
      } else {
        assert children.size() == 1
	def child = getBinOpOrConst(new IntLiteral(value:0, fileInfo: cur.fileInfo), BinOpType.SUB, children[0])
        child.fileInfo = cur.fileInfo
        parent.children << child
      }
      break

    case FLAT_EXPR:
      assert children.size() % 2 == 1
// We do distribution here in the following way:
//   if we determine that there's an induction variable in the flat expression,
//      then we will reassociate it to be on top
      def allLocs = children.findAll{ it instanceof Location && it.descriptor }
      def hasIV = allLocs.any{ it.descriptor.name in inductionVars }
      def canCommute = children.findAll{ it instanceof BinOpType }.every{ it == BinOpType.ADD || it == BinOpType.MUL }
      if (hasIV && canCommute) {
        // it doesn't matter if there are multiples, because we only care to distribute one of them
        def iv = children.find{ it instanceof Location && it.descriptor.name in inductionVars}
        def index = children.indexOf(iv)
        def indexLast = children.size()-1
        children.putAt(index, children[indexLast])
        children.putAt(indexLast, iv)
      }
      while (children.size() != 1) {
        def left = children.remove(0)
        def op = children.remove(0)
        def right = children.remove(0)
        def child = getBinOpOrConst(left, op, right)
        child.fileInfo = cur.fileInfo
        children.add(0, child)
      }
      parent.children << children[0]
      break

    case LOCATION:
      assert children.size() == 1 || children.size() == 2
      if (symTable[children[0]] == null) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Used variable ${children[0]} without declaring it"
        )
      }
      parent.children << new Location(
        descriptor: symTable[children[0]],
        indexExpr: children.size() == 2 ? children[1] : null,
        fileInfo: cur.fileInfo
      )
      break

    case METHOD_CALL:
      def methDesc = methodSymTable[cur.getText()]
      if (methDesc == null) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Used method ${cur.getText()} without declaring it"
        )
      } else if (methDesc.fileInfo.line > cur.fileInfo.line) {
        errors << new CompilerError(
          fileInfo: cur.fileInfo,
          message: "Used method ${cur.getText()} before it was declared on line $cur.fileInfo.line"
        )
      }
      parent.children << new MethodCall(
        descriptor: methDesc,
        params: children as List<Expr>,
        fileInfo: cur.fileInfo
      )
      break

    case ID:
      parent.children << cur.getText()
      if (parent.getType() == TK_for) {
        inductionVars << cur.getText()
      }
      break

    case METHOD_DECL:
      assert children.size() == 1
      methodSymTable[cur.getText()].block = children[0]
      methods[cur.getText()] = children[0]
      break

    case BLOCK:
      parent.children << new Block(
        symTable: symTable,
        statements: children as List<Statement>,
        fileInfo: cur.fileInfo
      )
      break

    case PLAIN_ASSIGN_OP:
    case MODIFY_ASSIGN_OP:
      parent.children << cur.getText()
      break

    //return or assignment, wtf david?
    case STATEMENT:
      switch (cur.getText()) {

      case 'assignment':
        assert children.size == 3
        Location lvalue = children[0]
        Expr rvalue = children[2]
        if (children[1] == '+=')
          rvalue = new BinOp(op:BinOpType.ADD, right: lvalue, left: rvalue, fileInfo: cur.fileInfo)
        else if (children[1] == '-=')
          rvalue = new BinOp(op:BinOpType.SUB, right: lvalue, left: rvalue, fileInfo: cur.fileInfo)
        parent.children << new Assignment(loc: lvalue, expr: rvalue, fileInfo: cur.fileInfo)
        break

      case 'return':
        assert children.size() <= 1
        def expr = null
        if (children.size() == 1)
          expr = children[0]
        parent.children << new Return(expr: expr, fileInfo: cur.fileInfo)
        break
      default:
        assert false
      }
      break

    case TK_if:
      assert children.size() == 2 || children.size() == 3
      def ifStmt = new IfThenElse(fileInfo: cur.fileInfo)
      ifStmt.condition = children[0]
      ifStmt.thenBlock = children[1]
      if (children.size() == 3)
        ifStmt.elseBlock = children[2]
      parent.children << ifStmt
      break

    case TK_for:
      assert children.size() == 4
      parent.children << new ForLoop(
        index: new Location(descriptor: symTable[children[0]], fileInfo: cur.fileInfo),
        low: children[1],
        high: children[2],
        block: children[3],
        symTable: symTable,
        fileInfo: cur.fileInfo
      )
      break

    case TK_break:
      parent.children << new Break(fileInfo: cur.fileInfo)
      break

    case TK_continue:
      parent.children << new Continue(fileInfo: cur.fileInfo)
      break

    case STRING_LITERAL:
      parent.children << new StringLiteral(value: cur.getText(), fileInfo: cur.fileInfo)
      break

    case TK_callout:
      assert children.size() >= 1
      children.each {
        assert it instanceof StringLiteral || it instanceof Expr
      }
      parent.children << new CallOut(
        name: children[0],
        params: children.subList(1, children.size()),
        fileInfo: cur.fileInfo
      )
      break

    case PROGRAM:
    case VAR_DECL:
    case TK_int:
    case TK_void:
    case TK_boolean:
    case ARRAY_DECL:
      break

    default:
      assert false, "Missing handler for ${cur.getType()}"
      break
    }
  }

  def getBinOpOrConst(left, op, right) {
    if (left instanceof IntLiteral && right instanceof IntLiteral) {
      try {
        switch (op) {
        case BinOpType.ADD:
          return new IntLiteral(value: left.value + right.value)
        case BinOpType.SUB:
          return new IntLiteral(value: left.value - right.value)
        case BinOpType.MUL:
          return new IntLiteral(value: left.value * right.value)
        case BinOpType.DIV:
          return new IntLiteral(value: left.value / right.value)
        case BinOpType.MOD:
          return new IntLiteral(value: left.value % right.value)
        case BinOpType.LT:
          return new BooleanLiteral(value: left.value < right.value)
        case BinOpType.GT:
          return new BooleanLiteral(value: left.value > right.value)
        case BinOpType.LTE:
          return new BooleanLiteral(value: left.value <= right.value)
        case BinOpType.GTE:
          return new BooleanLiteral(value: left.value >= right.value)
        case BinOpType.EQ:
          return new BooleanLiteral(value: left.value == right.value)
        case BinOpType.NEQ:
          return new BooleanLiteral(value: left.value != right.value)
        }
      } catch (ArithmeticException e) {
        throw new FatalException(msg: "During symbolic execution of constants in the program, we determined that division by zero is attempted. Please, change the program to avoid this.", code: 1)
      }
    } else if (left instanceof BooleanLiteral && right instanceof BooleanLiteral) {
      switch (op) {
      case BinOpType.EQ:
        return new BooleanLiteral(value: left.value == right.value)
      case BinOpType.NEQ:
        return new BooleanLiteral(value: left.value != right.value)
      case BinOpType.AND:
        return new BooleanLiteral(value: left.value && right.value)
      case BinOpType.OR:
        return new BooleanLiteral(value: left.value || right.value)
      }
    } else if (left instanceof BooleanLiteral && op == BinOpType.NOT) {
      return new BooleanLiteral(value: !left.value)
    } else {
      return new BinOp(op: op, left: left, right: right)
    }
  }
}
