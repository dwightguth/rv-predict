#
# This makefile should be built with `mkcmake`.  Install it with
# `apt-get install mk-configure`.
#
.-include "../mkbin.mk"

FILESDIR=$(DATADIR)/examples/rv-predict-c/78k0
INCSDIR=$(FILESDIR)
INCS=78k0_predict.h
FILES=Makefile
FILES+=README.md
FILES+=$(SRCS.complex)
FILES+=$(SRCS.lolo)
FILES+=$(SRCS.hilo)
FILES+=$(SRCS.simple)
FILESMODE=0644

.if $(MKBIN:Uno) == "yes"
PROGS+=	complex
PROGS+=	lolo
PROGS+=	hilo
PROGS+=	simple
.endif

LDFLAGS+=--intr-personality=78k0
CFLAGS+=--intr-personality=78k0
CFLAGS+=-std=c11
CPPFLAGS+=-D_POSIX_C_SOURCE=200809L
CPPFLAGS+=-I$(.CURDIR)/../c11
CPPFLAGS+=-I$(HOME)/include
CPPFLAGS+=-include 78k0_predict.h
WARNS=4

SRCS.complex=complex.c
SRCS.lolo=lolo.c
SRCS.hilo=hilo.c
SRCS.simple=simple.c

.include "../rvp.mk"

.include <mkc.prog.mk>
