PROG=   pax
CPPFLAGS +=	-DHAVE_NBTOOL_CONFIG_H
SRCS=	ar_io.c ar_subs.c buf_subs.c file_subs.c ftree.c\
	gen_subs.c getoldopt.c options.c pat_rep.c pax.c sel_subs.c tables.c\
	tar.c tty_subs.c

#SMALLPROG=1

.if defined(SMALLPROG)
CPPFLAGS+=	-DSMALL -DNO_CPIO
.else
SRCS+=		getid.c spec.c misc.c pack_dev.c cpio.c
CPPFLAGS+=	-I${.CURDIR}/../mtree \
		-I${.CURDIR}/../mknod
.PATH:		${.CURDIR}/../mtree \
		${.CURDIR}/../mknod

.if (${HOSTPROG:U} == "")
DPADD+= ${LIBUTIL}
LDADD+= -lutil
.endif
.endif

MAN=	pax.1 tar.1 cpio.1

.if defined(HOSTPROG)
CPPFLAGS+=	-DHOSTPROG
.else	# {	! HOSTPROG

# XXX: Interix does not have it; we need a conditional for it.
CPPFLAGS+=	-DHAVE_SYS_MTIO_H

.if ${MKBSDTAR:U} == "no"
LINKS+=	${BINDIR}/pax ${BINDIR}/tar
SYMLINKS+=${BINDIR}/tar /usr/bin/tar

LINKS+=	${BINDIR}/pax ${BINDIR}/cpio
SYMLINKS+=${BINDIR}/cpio /usr/bin/cpio
.endif
.endif	# }	! HOSTPROG

.if !defined(HOSTPROG) && !defined(SMALLPROG) && defined(NO_NO_NO)
CPPFLAGS+=	-DSUPPORT_RMT

LDADD+=	-lrmt
DPADD+=	${LIBRMT}
.endif

MKC_FEATURES =	strlcpy fgetln

.include "../mk/addlinks.mk"
