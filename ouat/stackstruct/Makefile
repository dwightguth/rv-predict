NOMAN=
MKINSTALL=no
.include "../compat.mk"

CC?=clang
PROG=stackstruct
SRCS=stackstruct.c foo.c
WARNS=5
COPTS+=-g -O0
LDADD+=$(DWARF_LDDIR) -ldwarf $(ELF_LDDIR) -lelf

.include <mkc.prog.mk>
