package decaf.optimizations
import decaf.*

class Peephole {
  //Delegate the DSL to the code generator
  CodeGenerator codegen

  def methodMissing(String name, args) {
    codegen.methodMissing(name, args)
  }

  def propertyMissing(String name) {
    codegen.propertyMissing(name)
  }

  int log2(int n) {
    if (n <= 0) throw new IllegalArgumentException()
    return 31 - Integer.numberOfLeadingZeros(n)
  }

  //p206 of software optimization for amd64 processors
  //dividend is in eax
  def divByConst(int divisor) {
    def d = Math.abs(divisor)
    def e = divisor
    if (e == -1) {
      neg(rax)
    } else if (d == 2) {
//todo: test
      cmp(0x80000000, rax)
      sbb(-1, rax)
      sar(1, rax)
      if (e < 0) neg(rax)
    } else if (!(d & (d - 1))) {
      cdq()
      and(d-1, rdx)
      add(rdx, rax)
      if (log2(d) != 0) sar(log2(d), rax)
      if (e < 0) neg(rax)
    } else {
      def l = log2(d)
      def j = (long)(0x80000000 % d)
      def k = (long)((1 << (32 + l)) / (0x80000000 - j))
      def m_low = (long)((1 << (32 + l)) / d)
      def m_high = (long)(((1 << (32 + l)) + k) / d)
      while (((m_low >> 1) < (m_high >> 1)) && (l > 0)) {
        m_low = m_low >> 1
        m_high = m_high >> 1
        l = l - 1
      }
      def m = m_high & 0xffffffff
      def s = l
      def a = (m_high >> 31) != 0 ? 1 : 0
      push(rbx)
      movq(rbx, rax) //dividend in rbx
      movq(m, rax)
      imul(rbx, rax)
      movq(rbx, rax)
      if (a != 0) add(rax, rdx) //switch?
      if (s != 0) sar(s, rdx)
      shr(31, rax)
      add(rdx, rax)
      if (e < 0) neg(rax)
      pop(rbx)
    }
  }
}
