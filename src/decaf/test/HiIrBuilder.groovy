package decaf.test
import decaf.*
import groovytools.builder.*
import static decaf.BinOpType.*

class HiIrBuilder extends BuilderSupport {
  SymbolTable symTable = new SymbolTable()
  SymbolTable methodSymTable = new SymbolTable()

  FileInfo nullFI = new FileInfo(line: -1, col: -1)

  void setParent(Object parent, Object child) {
    switch (parent) {
      case Block:
        //var decls
        if (child == null) return
        assert child instanceof Statement
        parent.statements << child
        break
      case BinOp:
        assert child instanceof Expr
        if (parent.left == null)
          parent.left = child
        else if (parent.right == null)
          parent.right = child
        else
          assert false
        break
      case CallOut:
        assert child instanceof Expr || child instanceof StringLiteral
        parent.params << child
        break
      case MethodCall:
        assert child instanceof Expr
        parent.params << child
        break
      case Assignment:
        if (parent.loc == null) {
          assert child instanceof Location
          parent.loc = child
        } else if (parent.expr == null) {
          assert child instanceof Expr
          parent.expr = child
        } else {
          assert false
        }
        break
      case Return:
        assert child instanceof Expr
        assert parent.expr == null
        parent.expr = child
        break
      case IfThenElse:
        if (parent.condition == null) {
          assert child instanceof Expr
          parent.condition = child
        } else if (parent.thenBlock == null) {
          assert child instanceof Block
          parent.thenBlock = child
        } else if (parent.elseBlock == null) {
          assert child instanceof Block
          parent.elseBlock = child
        } else {
          assert false
        }
        break
      case ForLoop:
        if (parent.low == null) {
          assert child instanceof Expr
          parent.low = child
        } else if (parent.high == null) {
          assert child instanceof Expr
          parent.high = child
        } else if (parent.block == null) {
          assert child instanceof Block
          parent.block = child
        } else {
          assert false
        }
        break
      case Location:
        if (parent.indexExpr == null) {
          parent.indexExpr = child
        } else {
          assert false
        }
        break
      default:
        assert false
    }
    child.parent = parent
  }

  Object createNode(Object name) {
    return createNode(name, [:], null)
  }

  Object createNode(Object name, Object value) {
    return createNode(name, [:], value)
  }
  
  Object createNode(Object name, Map attributes) {
    return createNode(name, attributes, attributes.value)
  }
  
  Object createNode(Object name, Map attributes, Object value) {
    def ret
    switch (name) {
    case 'var':
      def desc = new VariableDescriptor(fileInfo: nullFI, arraySize: attributes.arraySize, type: attributes.type, name: value ?: attributes.name)
      symTable[desc.name] = desc
      return
    case 'method':
      def desc = new MethodDescriptor(name: attributes.name, returnType: attributes.returns)
      def params = attributes.takes.collect {
        new VariableDescriptor(type: it, fileInfo: nullFI)
      }
      desc.params = params
      methodSymTable[desc.name] = desc
      return
    case 'BinOp':
      ret = new BinOp(op: value)
      break
    case 'lit':
    case 'literal':
      switch(value) {
      case Integer: ret = new IntLiteral(value: value); break
      case Boolean: ret = new BooleanLiteral(value: value); break
      case String: ret = new StringLiteral(value: value); break
      default: throw new RuntimeException("literal must be int, boolean, or String")
      }
      break
    case 'CallOut':
      ret = new CallOut(name: value)
      break
    case 'Assignment':
      ret = new Assignment()
      break
    case 'Location':
      def desc = symTable[value]
      assert desc != null
      ret = new Location(descriptor: desc)
      break
    case 'Return':
      ret = new Return()
      break
    case 'Block':
      symTable = new SymbolTable(symTable)
      ret = new Block()
      break
    case 'Break':
      ret = new Break()
      break
    case 'Continue':
      ret = new Continue()
      break
    case 'IfThenElse':
      ret = new IfThenElse()
      break
    case 'ForLoop':
      symTable = new SymbolTable(symTable)
      def index = new VariableDescriptor(name: attributes.index, type:Type.INT, fileInfo: nullFI)
      symTable[attributes.index] = index
      ret = new ForLoop(index: new Location(descriptor: index))
      ret.index.parent = ret
      break
    case 'MethodCall':
      ret = new MethodCall(descriptor: methodSymTable[value])
      break
    default:
      throw new RuntimeException("Unknown node: $name")
    }
    if (attributes.containsKey('line')) {
      ret.fileInfo = new FileInfo(line: attributes.line, col: 0)
    } else {
      ret.fileInfo = nullFI
    }
    return ret
  }

  void nodeCompleted(Object parent, Object node) {
    switch (node) {
    case ForLoop:
    case Block:
      symTable = symTable.parent
      break
    }
  }

  static void main(args) {
    //Example usage
    HiIrBuilder hb = new HiIrBuilder()
    Block b = hb.Block{
      var(name:'a', type:Type.INT)
      method(name:'foo', returns: Type.INT, takes: [Type.BOOLEAN, Type.INT])
      Block() {
        MethodCall('foo') { lit(true); lit(3) }
      }
      Assignment() {
        Location('a')
        BinOp(ADD) {
          lit(3)
          BinOp(MUL) {
            lit(1); lit(18)
          }
        }
      }
      CallOut('printf') {
        lit("formatstr")
        lit(3)
        BinOp(DIV) { lit(2); lit(4) }
      }
      Return()
      Return() {
        lit(3)
      }
      ForLoop(index: 'i') {
        lit(1); lit(10); Block()
      }
      IfThenElse() {
        lit(true)
        Block() { Break() }
        Block() { Continue() }
      }
      IfThenElse() {
        lit(false)
        Block() { Break() }
      }
    }
  }
}
