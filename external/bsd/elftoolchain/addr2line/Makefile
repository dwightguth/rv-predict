# $Id: Makefile 2066 2011-10-26 15:40:28Z jkoshy $

.include <bsd.own.mk>

TOP=	$(.CURDIR)/..

PROG=	addr2line
SRCS=	addr2line.c

WARNS?=	6

DPADD=	$(LIBELF) $(LIBELFTC) $(LIBDWARF)
LDADD=	-L$(LIBELFTC) -lelftc -L$(LIBDWARF) -ldwarf -L$(LIBELF) -lelf

MAN1=	addr2line.1

.include "${TOP}/mk/elftoolchain.prog.mk"
