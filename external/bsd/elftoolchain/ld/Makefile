# $Id: Makefile 3412 2016-02-16 21:52:09Z jkoshy $

.include <bsd.own.mk>

TOP=	$(.CURDIR)/..

PROG=	ld
WARNS?=	5

SRCS=	amd64.c			\
	amd64_script.c		\
	i386.c			\
	i386_script.c		\
	ld_arch.c		\
	ld_dynamic.c		\
	ld_ehframe.c		\
	ld_error.c 		\
	ld_exp.c		\
	ld_file.c		\
	ld_hash.c		\
	ld_input.c		\
	ld_layout.c		\
	ld_main.c 		\
	ld_options.c		\
	ld_output.c		\
	ld_path.c		\
	ld_reloc.c		\
	ld_script.c		\
	ld_strtab.c		\
	ld_symbols.c		\
	ld_symver.c		\
	mips.c			\
	littlemips_script.c	\
	bigmips_script.c

LSRC= ld_script_lexer.l
YSRC= ld_script_parser.y

GENSRCS=	amd64_script.c i386_script.c littlemips_script.c \
		bigmips_script.c

CLEANFILES+=	${GENSRCS}

DPADD=	${LIBELFTC} ${LIBELF} ${LIBDWARF}
LDADD=	-L$(LIBELFTC) -lelftc -L$(LIBDWARF) -ldwarf -L$(LIBELF) -lelf

CFLAGS+= -I. -I${.CURDIR}
YFLAGS=	-d

.SUFFIXES:	.ld .c
.ld.c:
	awk -v BASENAME=${.ALLSRC:T} -f ${.CURDIR}/ld_script.awk ${.ALLSRC} > ${.TARGET}

.include "${TOP}/mk/elftoolchain.prog.mk"
