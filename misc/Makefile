#
# This makefile uses BSD macro files.  You can get a rudimentary
# set of BSD macro files on POSIX-compliant platforms like Linux and
# Mac OS X by installing a Pkgsrc bootstrap kit from pkgsrc.org.
#

PROGS=eitherorboth fork pass_array raise sigcount

CC=clang
CFLAGS+=-std=c11
COPTS+=-O3
LDADD+=-pthread
COPTS+=-g
LDADD+=-g
CPPFLAGS+=-D_POSIX_C_SOURCE=199506L
CPPFLAGS+=-I$(.CURDIR)/../include
SRCS.eitherorboth=eitherorboth.c
SRCS.sigcount=sigcount.c
SRCS.raise=raise.c
SRCS.fork=fork.c
SRCS.pass_array=pass_array.c
WARNS=4

.include <mkc.prog.mk>
