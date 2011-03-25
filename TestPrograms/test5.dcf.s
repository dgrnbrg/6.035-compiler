.text
.globl fib
fib:
enter $112, $0
label29:
movq 16(%rbp), %r10
movq %r10, -16(%rbp)
movq $2, -24(%rbp)
movq -16(%rbp), %r10
movq -24(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmovl %r10, %r11
movq %r11, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label3
label5:
movq 16(%rbp), %r10
movq %r10, -64(%rbp)
movq $1, -72(%rbp)
movq -64(%rbp), %r10
movq -72(%rbp), %r11
sub %r11, %r10
movq %r10, -56(%rbp)
sub $8, %rsp
movq -56(%rbp), %r10
movq %r10, 0(%rsp)
call fib
movq %rax, -48(%rbp)
add $8, %rsp
movq 16(%rbp), %r10
movq %r10, -96(%rbp)
movq $2, -104(%rbp)
movq -96(%rbp), %r10
movq -104(%rbp), %r11
sub %r11, %r10
movq %r10, -88(%rbp)
sub $8, %rsp
movq -88(%rbp), %r10
movq %r10, 0(%rsp)
call fib
movq %rax, -80(%rbp)
add $8, %rsp
movq -48(%rbp), %r10
movq -80(%rbp), %r11
add %r10, %r11
movq %r11, -40(%rbp)
movq -40(%rbp), %rax
leave
ret
jmp fib_end
label3:
movq $1, -32(%rbp)
movq -32(%rbp), %rax
leave
ret
jmp label5
fib_end:
movq $.label0, %rdi
movq $0, %rax
call printf
movq $1, %rdi
call exit
.globl main
main:
enter $192, $0
label62:
movq $0, -32(%rbp)
sub $8, %rsp
movq -32(%rbp), %r10
movq %r10, 0(%rsp)
call fib
movq %rax, -24(%rbp)
add $8, %rsp
movq $1, -40(%rbp)
movq -24(%rbp), %r10
movq -40(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -16(%rbp)
movq $13, -48(%rbp)
sub $16, %rsp
movq -16(%rbp), %r10
movq %r10, 0(%rsp)
movq -48(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -8(%rbp)
add $16, %rsp
movq $1, -80(%rbp)
sub $8, %rsp
movq -80(%rbp), %r10
movq %r10, 0(%rsp)
call fib
movq %rax, -72(%rbp)
add $8, %rsp
movq $1, -88(%rbp)
movq -72(%rbp), %r10
movq -88(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -64(%rbp)
movq $14, -96(%rbp)
sub $16, %rsp
movq -64(%rbp), %r10
movq %r10, 0(%rsp)
movq -96(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -56(%rbp)
add $16, %rsp
movq $2, -128(%rbp)
sub $8, %rsp
movq -128(%rbp), %r10
movq %r10, 0(%rsp)
call fib
movq %rax, -120(%rbp)
add $8, %rsp
movq $2, -136(%rbp)
movq -120(%rbp), %r10
movq -136(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -112(%rbp)
movq $15, -144(%rbp)
sub $16, %rsp
movq -112(%rbp), %r10
movq %r10, 0(%rsp)
movq -144(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -104(%rbp)
add $16, %rsp
movq $3, -176(%rbp)
sub $8, %rsp
movq -176(%rbp), %r10
movq %r10, 0(%rsp)
call fib
movq %rax, -168(%rbp)
add $8, %rsp
movq $3, -184(%rbp)
movq -168(%rbp), %r10
movq -184(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -160(%rbp)
movq $16, -192(%rbp)
sub $16, %rsp
movq -160(%rbp), %r10
movq %r10, 0(%rsp)
movq -192(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -152(%rbp)
add $16, %rsp
jmp main_end
main_end:
movq $0, %rax
leave
ret
.globl assert
assert:
enter $48, $0
label78:
movq 16(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label65
movq $.label1, -24(%rbp)
movq 24(%rbp), %r10
movq %r10, -32(%rbp)
movq -24(%rbp), %rdi
movq -32(%rbp), %rsi
movq $0, %rax
call printf
movq %rax, -16(%rbp)
label73:
jmp assert_end
label65:
leave
ret
jmp label73
assert_end:
movq $0, %rax
leave
ret
.data
.label0:
.string "RUNTIME ERROR: Control fell off end of non-void function fib\n"
.label1:
.string "DECAF ASSERT FAILED on LINE NUMBER: %d\n"
.bss
