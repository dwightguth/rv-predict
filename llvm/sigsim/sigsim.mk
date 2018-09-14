.include "../../cross-libdir.mk"

LIB?=rvpsigsim_$(SIGSIM)

.PATH: $(.CURDIR)/../../ngrt

TARGET_CC?=clang
CC?=$(TARGET_CC)
.if $(CC) == gcc
CPPFLAGS+=-U_FORTIFY_SOURCE
.endif
CFLAGS+=-std=c11
CFLAGS+=-pedantic
CFLAGS+=-Wmissing-prototypes
CFLAGS+=-O3
CFLAGS+=-g
CFLAGS+=-Wmissing-prototypes
CPPFLAGS+=-I$(.CURDIR)/../../ngrt -I$(.CURDIR)/../../../include
CPPFLAGS+=-D_GNU_SOURCE	# for <dlfcn.h> constant RTLD_NEXT
SRCS+=sigsim_$(SIGSIM).c
LINKS=${LIBDIR}/librvpsigsim_$(SIGSIM).a ${LIBDIR}/librvpsigsim_$(SIGSIM)64.a

.include <mkc.lib.mk>
