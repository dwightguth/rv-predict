# $Id: Makefile 2069 2011-10-26 15:53:48Z jkoshy $

.include <bsd.own.mk>

TOP=	${.CURDIR}/..

PROG=	findtextrel
SRCS=	findtextrel.c

WARNS?=	6

DPADD=	${LIBELFTC} ${LIBDWARF} ${LIBELF} 
LDADD=	-L$(LIBELFTC) -lelftc -L$(LIBDWARF) -ldwarf -L$(LIBELF) -lelf

MAN1=	findtextrel.1

.include "${TOP}/mk/elftoolchain.prog.mk"
