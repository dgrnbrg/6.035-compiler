.text
.globl foo
foo:
enter $24, $0
label6:
movq $1, -16(%rbp)
movq -16(%rbp), %r10
movq %r10, c_globalvar
movq $1, -24(%rbp)
movq -24(%rbp), %rax
leave
ret
jmp foo_end
foo_end:
movq $.label0, %rdi
movq $0, %rax
call printf
movq $1, %rdi
call exit
.globl main
main:
enter $224, $0
label79:
movq $0, -24(%rbp)
movq -24(%rbp), %r10
movq %r10, c_globalvar
movq $0, -48(%rbp)
movq -48(%rbp), %r11
cmp $1, %r11
je label16
label14:
movq $0, -40(%rbp)
label15:
movq -40(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r10
movq %r10, -88(%rbp)
movq -88(%rbp), %r10
xor $1, %r10
movq %r10, -80(%rbp)
movq -80(%rbp), %r11
cmp $1, %r11
je label31
label29:
movq $0, -72(%rbp)
label30:
movq $15, -120(%rbp)
sub $16, %rsp
movq -72(%rbp), %r10
movq %r10, 0(%rsp)
movq -120(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -64(%rbp)
add $16, %rsp
movq $0, -136(%rbp)
movq -136(%rbp), %r10
movq %r10, c_globalvar
movq $1, -160(%rbp)
movq -160(%rbp), %r11
cmp $1, %r11
je label50
sub $0, %rsp
call foo
movq %rax, -168(%rbp)
add $0, %rsp
movq -168(%rbp), %r11
cmp $1, %r11
je label50
movq $0, -152(%rbp)
label52:
movq -152(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r10
movq %r10, -192(%rbp)
movq -192(%rbp), %r11
cmp $1, %r11
je label67
label65:
movq $0, -184(%rbp)
label66:
movq $19, -224(%rbp)
sub $16, %rsp
movq -184(%rbp), %r10
movq %r10, 0(%rsp)
movq -224(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -176(%rbp)
add $16, %rsp
jmp main_end
label16:
sub $0, %rsp
call foo
movq %rax, -56(%rbp)
add $0, %rsp
movq -56(%rbp), %r11
cmp $1, %r11
je label13
jmp label14
label31:
movq c_globalvar, %r10
movq %r10, -104(%rbp)
movq $0, -112(%rbp)
movq -104(%rbp), %r10
movq -112(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -96(%rbp)
movq -96(%rbp), %r11
cmp $1, %r11
je label28
jmp label29
label50:
movq $1, -152(%rbp)
jmp label52
label67:
movq c_globalvar, %r10
movq %r10, -208(%rbp)
movq $0, -216(%rbp)
movq -208(%rbp), %r10
movq -216(%rbp), %r11
cmp %r11, %r10
movq $1, %r10
movq $0, %r11
cmove %r10, %r11
movq %r11, -200(%rbp)
movq -200(%rbp), %r11
cmp $1, %r11
je label64
jmp label65
label13:
movq $1, -40(%rbp)
jmp label15
label28:
movq $1, -72(%rbp)
jmp label30
label64:
movq $1, -184(%rbp)
jmp label66
main_end:
movq $0, %rax
leave
ret
.globl assert
assert:
enter $48, $0
label95:
movq 16(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label82
movq $.label1, -24(%rbp)
movq 24(%rbp), %r10
movq %r10, -32(%rbp)
movq -24(%rbp), %rdi
movq -32(%rbp), %rsi
movq $0, %rax
call printf
movq %rax, -16(%rbp)
label90:
jmp assert_end
label82:
leave
ret
jmp label90
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
.comm c_globalvar 8
