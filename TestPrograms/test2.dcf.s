.text
.globl main
main:
enter $288, $0
label49:
movq $4, -32(%rbp)
movq -32(%rbp), %r10
movq %r10, -224(%rbp)
movq -224(%rbp), %r10
movq %r10, -48(%rbp)
movq $4, -56(%rbp)
movq -48(%rbp), %r10
movq -56(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -40(%rbp)
movq -40(%rbp), %r11
cmp $1, %r11
je label6
movq $6, -120(%rbp)
movq -120(%rbp), %r10
movq %r10, -232(%rbp)
label11:
movq -232(%rbp), %r10
movq %r10, -144(%rbp)
movq $5, -152(%rbp)
movq -144(%rbp), %r10
movq -152(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -136(%rbp)
movq $13, -160(%rbp)
sub $16, %rsp
movq -136(%rbp), %r10
movq %r10, 0(%rsp)
movq -160(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -128(%rbp)
add $16, %rsp
movq -232(%rbp), %r10
movq %r10, -176(%rbp)
movq $5, -184(%rbp)
movq -176(%rbp), %r10
movq -184(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -168(%rbp)
movq -168(%rbp), %r11
cmp $1, %r11
je label29
movq $2, -248(%rbp)
movq -248(%rbp), %r10
movq %r10, -224(%rbp)
label34:
movq -224(%rbp), %r10
movq %r10, -272(%rbp)
movq $1, -280(%rbp)
movq -272(%rbp), %r10
movq -280(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -264(%rbp)
movq $21, -288(%rbp)
sub $16, %rsp
movq -264(%rbp), %r10
movq %r10, 0(%rsp)
movq -288(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -256(%rbp)
add $16, %rsp
jmp main_end
label6:
movq $5, -88(%rbp)
movq -88(%rbp), %r10
movq %r10, -232(%rbp)
jmp label11
label29:
movq $1, -216(%rbp)
movq -216(%rbp), %r10
movq %r10, -224(%rbp)
jmp label34
main_end:
movq $0, %rax
leave
ret
.globl assert
assert:
enter $48, $0
label65:
movq 16(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label52
movq $.label0, -24(%rbp)
movq 24(%rbp), %r10
movq %r10, -32(%rbp)
movq -24(%rbp), %rdi
movq -32(%rbp), %rsi
movq $0, %rax
call printf
movq %rax, -16(%rbp)
label60:
jmp assert_end
label52:
leave
ret
jmp label60
assert_end:
movq $0, %rax
leave
ret
.data
.label0:
.string "DECAF ASSERT FAILED on LINE NUMBER: %d\n"
.bss
