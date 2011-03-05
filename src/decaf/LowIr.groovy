package decaf

class LowIrStatement{}

class LowIrBlock{
  String id
  List<LowIrStatement> statements = []
  LowIrBlock fallthroughBlock
}

class SSAVar{
  int id
}

class Allocate{
  VariableDescriptor descriptor
}

class Phi{
  List<SSAVar> choices
  SSAVar result
}

class CallMethod{
  MethodDescriptor descriptor
  List<SSAVar> arguments
  SSAVar result
}

class Callout{
  StringLiteral name
  List<SSAVar> arguments
  SSAVar result
}

class LowIrBinOp{
  SSAVar left
  SSAVar right
  // Ensure that HiIr.groovy is actually imported
  BinOpType op
  SSAVar result
}

class LoadConstant{
  int constant
  SSAVar result 
}

class UnconditionalJump{
  LowIrBlock destination
}

class ConditionalJump{
  SSAVar condition
  LowIrBlock destination
}

class LowIrReturn{
}

// For non-SSA control flow graph
class LoadVariable{
  SSAVar result
  VariableDescriptor variable
}

class StoreVariable{
  SSAVar valueToStore
  VariableDescriptor destination
}
