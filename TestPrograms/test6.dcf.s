.text
.globl main
main:
enter $208, $0
label53:
movq $0, -32(%rbp)
movq -32(%rbp), %r10
movq %r10, -16(%rbp)
movq $1, -56(%rbp)
movq -56(%rbp), %r10
movq %r10, -40(%rbp)
movq $5, -64(%rbp)
label9:
movq -40(%rbp), %r10
movq -64(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmovl %r10, %r11
movq %r11, -208(%rbp)
movq -208(%rbp), %r11
cmp $1, %r11
je label43
label11:
movq -16(%rbp), %r10
movq %r10, -168(%rbp)
movq $3, -176(%rbp)
movq -168(%rbp), %r10
movq -176(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -160(%rbp)
movq $18, -184(%rbp)
sub $16, %rsp
movq -160(%rbp), %r10
movq %r10, 0(%rsp)
movq -184(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -152(%rbp)
add $16, %rsp
jmp main_end
label43:
movq -40(%rbp), %r10
movq %r10, -80(%rbp)
movq $3, -88(%rbp)
movq -80(%rbp), %r10
movq -88(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmovg %r10, %r11
movq %r11, -72(%rbp)
movq -72(%rbp), %r11
cmp $1, %r11
je label14
movq -40(%rbp), %r10
movq %r10, -104(%rbp)
movq $3, -112(%rbp)
movq -104(%rbp), %r10
movq -112(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmovl %r10, %r11
movq %r11, -96(%rbp)
movq -96(%rbp), %r11
cmp $1, %r11
je label25
movq -40(%rbp), %r10
movq %r10, -136(%rbp)
movq -16(%rbp), %r10
movq %r10, -120(%rbp)
movq -136(%rbp), %r10
movq -120(%rbp), %r11
add %r10, %r11
movq %r11, -128(%rbp)
movq -128(%rbp), %r10
movq %r10, -16(%rbp)
label6:
movq $1, -192(%rbp)
movq -40(%rbp), %r10
movq -192(%rbp), %r11
add %r10, %r11
movq %r11, -200(%rbp)
movq -200(%rbp), %r10
movq %r10, -40(%rbp)
jmp label9
label14:
jmp label11
label25:
jmp label6
main_end:
movq $0, %rax
leave
ret
.globl assert
assert:
enter $48, $0
label69:
movq 16(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label56
movq $.label0, -24(%rbp)
movq 24(%rbp), %r10
movq %r10, -32(%rbp)
movq -24(%rbp), %rdi
movq -32(%rbp), %rsi
movq $0, %rax
call printf
movq %rax, -16(%rbp)
label64:
jmp assert_end
label56:
leave
ret
jmp label64
assert_end:
movq $0, %rax
leave
ret
.data
.label0:
.string "DECAF ASSERT FAILED on LINE NUMBER: %d\n"
.bss
