package decaf

class LowerBlock{
  String id
  List<LowerStatement> statements = []
  LowerBlock fallthroughBlock
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

class LowerBinOp{
  SSAVar left
  SSAVar right
  // Ensure that HiIr.groovy is actually imported
  BinOpType op
  SSAVar result
}

class UnconditionalJump{
  LowerBlock destination
}

class ConditionalJump{
  SSAVar condition
  LowerBlock destination
}

class LowerReturn{
}