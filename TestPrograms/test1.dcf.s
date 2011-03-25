.text
.globl main
main:
enter $96, $0
label16:
movq $1, -16(%rbp)
movq $5, -24(%rbp)
sub $16, %rsp
movq -16(%rbp), %r10
movq %r10, 0(%rsp)
movq -24(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -8(%rbp)
add $16, %rsp
movq $1, -40(%rbp)
movq $6, -48(%rbp)
sub $16, %rsp
movq -40(%rbp), %r10
movq %r10, 0(%rsp)
movq -48(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -32(%rbp)
add $16, %rsp
movq $1, -64(%rbp)
movq $7, -72(%rbp)
sub $16, %rsp
movq -64(%rbp), %r10
movq %r10, 0(%rsp)
movq -72(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -56(%rbp)
add $16, %rsp
movq $1, -88(%rbp)
movq $9, -96(%rbp)
sub $16, %rsp
movq -88(%rbp), %r10
movq %r10, 0(%rsp)
movq -96(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -80(%rbp)
add $16, %rsp
jmp main_end
main_end:
movq $0, %rax
leave
ret
.globl assert
assert:
enter $48, $0
label32:
movq 16(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label19
movq $.label0, -24(%rbp)
movq 24(%rbp), %r10
movq %r10, -32(%rbp)
movq -24(%rbp), %rdi
movq -32(%rbp), %rsi
movq $0, %rax
call printf
movq %rax, -16(%rbp)
label27:
jmp assert_end
label19:
leave
ret
jmp label27
assert_end:
movq $0, %rax
leave
ret
.data
.label0:
.string "DECAF ASSERT FAILED on LINE NUMBER: %d\n"
.bss
