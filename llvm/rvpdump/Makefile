#
# This makefile uses `mk-configure` macro files
# (https://github.com/cheusov/mk-configure) with BSD make.  On Ubuntu
# Linux, install `mk-configure` and BSD make with `apt-get install
# mk-configure`.
#
# On other POSIX-compliant platforms like Mac OS X and *BSD, you can
# install bmake and mk-configure from pkgsrc.org. bmake is in the pkgsrc
# bootstrap kit. The package for mk-configure is in devel/.
#

CC?=$(HOST_CC)

.PATH: $(.CURDIR)/../ngrt

PROG=rvpdump
SRCS=main.c

.include <mkc.prog.mk>

CPPFLAGS+=-D_POSIX_C_SOURCE=200809L	# for dprintf(3) and getopt(3)
CPPFLAGS+=-Wuninitialized -I${.CURDIR}/../ngrt -I${.CURDIR}/../../include
CPPFLAGS+=-I$(.CURDIR)/../librvread
SRCS=main.c
WARNS=4
STRIPFLAG=

COPTS+=-g -O3

