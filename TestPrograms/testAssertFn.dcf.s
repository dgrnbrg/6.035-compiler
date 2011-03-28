.text
.globl main
main:
enter $24, $0
label4:
movq $0, -16(%rbp)
movq $4, -24(%rbp)
sub $16, %rsp
movq -16(%rbp), %r10
movq %r10, 0(%rsp)
movq -24(%rbp), %r10
movq %r10, 8(%rsp)
call assert
movq %rax, -8(%rbp)
add $16, %rsp
jmp main_end
main_end:
movq $0, %rax
leave
ret
.globl assert
assert:
enter $64, $0
label23:
movq 16(%rbp), %r10
movq %r10, -8(%rbp)
movq -8(%rbp), %r11
cmp $1, %r11
je label7
movq $.label0, -24(%rbp)
movq 24(%rbp), %r10
movq %r10, -32(%rbp)
movq -24(%rbp), %rdi
movq -32(%rbp), %rsi
movq $0, %rax
call printf
movq %rax, -16(%rbp)
movq $1, -48(%rbp)
movq -48(%rbp), %rdi
call exit
movq %rax, -40(%rbp)
label18:
jmp assert_end
label7:
leave
ret
jmp label18
assert_end:
movq $0, %rax
leave
ret
.data
.label0:
.string "DECAF ASSERT FAILED on LINE NUMBER: %d\n"
.bss
