# $Id: Makefile 3316 2016-01-11 22:48:22Z jkoshy $

.include <bsd.own.mk>

TOP=	$(.CURDIR)/..

.include "${TOP}/mk/elftoolchain.components.mk"

PROG=	elfcopy

SRCS=	archive.c ascii.c binary.c main.c sections.c segments.c \
	symbols.c

WARNS?=	5

DPADD=	${LIBELF} ${LIBELFTC}
LDADD=	-L$(LIBELF) -lelf -L$(LIBELFTC) -lelftc

.if !defined(LIBELF_AR)
LDADD+= -larchive
.endif

.if defined(WITH_PE) && ${WITH_PE:tl} == "yes"
SRCS+=	pe.c
CFLAGS+= -DWITH_PE=1

DPADD+=	${LIBPE}
LDADD+=	-lpe
.endif

MAN=	elfcopy.1 mcs.1 strip.1

NO_SHARED?=	yes

LINKS=	${BINDIR}/elfcopy ${BINDIR}/strip 	\
	${BINDIR}/elfcopy ${BINDIR}/mcs

EXTRA_TARGETS=	strip mcs

CLEANFILES+=	${EXTRA_TARGETS}

# Create in-place symbolic links to "elfcopy" at build time.

all:	${EXTRA_TARGETS}

${EXTRA_TARGETS}:	${PROG}
	ln -s ${PROG} ${.TARGET}

.include "${TOP}/mk/elftoolchain.prog.mk"

.if ${OS_HOST} == "OpenBSD"
CFLAGS+=	-I/usr/local/include
LDFLAGS+=	-L/usr/local/lib
.endif
