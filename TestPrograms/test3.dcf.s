.text
.globl main
main:
enter $160, $0
label31:
movq $0, -32(%rbp)
movq -32(%rbp), %r10
movq %r10, -16(%rbp)
movq $1, -56(%rbp)
movq -56(%rbp), %r10
movq %r10, -40(%rbp)
movq $10, -64(%rbp)
label9:
movq -40(%rbp), %r10
movq -64(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmovl %r10, %r11
movq %r11, -160(%rbp)
movq -160(%rbp), %r11
cmp $1, %r11
je label21
movq -16(%rbp), %r10
movq %r10, -120(%rbp)
movq $45, -128(%rbp)
movq -120(%rbp), %r10
movq -128(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -112(%rbp)
movq $11, -136(%rbp)
sub $16, %rsp
movq -112(%rbp), %r10
movq %r10, 0(%rsp)
movq -136(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -104(%rbp)
add $16, %rsp
jmp main_end
label21:
movq -40(%rbp), %r10
movq %r10, -88(%rbp)
movq -16(%rbp), %r10
movq %r10, -72(%rbp)
movq -88(%rbp), %r10
movq -72(%rbp), %r11
add %r10, %r11
movq %r11, -80(%rbp)
movq -80(%rbp), %r10
movq %r10, -16(%rbp)
movq $1, -144(%rbp)
movq -40(%rbp), %r10
movq -144(%rbp), %r11
add %r10, %r11
movq %r11, -152(%rbp)
movq -152(%rbp), %r10
movq %r10, -40(%rbp)
jmp label9
main_end:
movq $0, %rax
leave
ret
.globl assert
assert:
enter $48, $0
label47:
movq 16(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label34
movq $.label0, -24(%rbp)
movq 24(%rbp), %r10
movq %r10, -32(%rbp)
movq -24(%rbp), %rdi
movq -32(%rbp), %rsi
movq $0, %rax
call printf
movq %rax, -16(%rbp)
label42:
jmp assert_end
label34:
leave
ret
jmp label42
assert_end:
movq $0, %rax
leave
ret
.data
.label0:
.string "DECAF ASSERT FAILED on LINE NUMBER: %d\n"
.bss
