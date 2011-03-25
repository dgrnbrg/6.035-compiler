.text
.globl foo
foo:
enter $56, $0
label17:
movq 16(%rbp), %r10
movq %r10, -16(%rbp)
movq 24(%rbp), %r10
movq %r10, -24(%rbp)
movq -16(%rbp), %r10
movq -24(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label3
movq $0, -40(%rbp)
movq -40(%rbp), %rax
leave
ret
label8:
jmp foo_end
label3:
movq $1, -32(%rbp)
movq -32(%rbp), %rax
leave
ret
jmp label8
foo_end:
movq $.label0, %rdi
movq $0, %rax
call printf
movq $1, %rdi
call exit
.globl main
main:
enter $88, $0
label33:
movq $3, -24(%rbp)
movq $3, -32(%rbp)
sub $16, %rsp
movq -24(%rbp), %r10
movq %r10, 0(%rsp)
movq -32(%rbp), %r10
movq %r10, 8(%rsp)
call foo
movq %rax, -16(%rbp)
add $16, %rsp
movq $12, -40(%rbp)
sub $16, %rsp
movq -16(%rbp), %r10
movq %r10, 0(%rsp)
movq -40(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -8(%rbp)
add $16, %rsp
movq $3, -72(%rbp)
movq $2, -80(%rbp)
sub $16, %rsp
movq -72(%rbp), %r10
movq %r10, 0(%rsp)
movq -80(%rbp), %r10
movq %r10, 8(%rsp)
call foo
movq %rax, -64(%rbp)
add $16, %rsp
movq -64(%rbp), %r10
xor $1, %r10
movq %r10, -56(%rbp)
movq $13, -88(%rbp)
sub $16, %rsp
movq -56(%rbp), %r10
movq %r10, 0(%rsp)
movq -88(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -48(%rbp)
add $16, %rsp
jmp main_end
main_end:
movq $0, %rax
leave
ret
.globl assert
assert:
enter $48, $0
label49:
movq 16(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label36
movq $.label1, -24(%rbp)
movq 24(%rbp), %r10
movq %r10, -32(%rbp)
movq -24(%rbp), %rdi
movq -32(%rbp), %rsi
movq $0, %rax
call printf
movq %rax, -16(%rbp)
label44:
jmp assert_end
label36:
leave
ret
jmp label44
assert_end:
movq $0, %rax
leave
ret
.data
.label0:
.string "RUNTIME ERROR: Control fell off end of non-void function foo\n"
.label1:
.string "DECAF ASSERT FAILED on LINE NUMBER: %d\n"
.bss
