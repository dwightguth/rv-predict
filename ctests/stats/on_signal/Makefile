NOMAN=
MKINSTALL=no
.include "../../../compat.mk"

PATH:=$(.OBJDIR)/../../toolset/bin:$(PATH)
.export PATH

PREDICT_CC?=rvpc
CC=$(PREDICT_CC)
LDFLAGS+=-pthread	# needed if CC is overridden with clang or gcc
PROG=on_signal
WARNS=5
CPPFLAGS+=-I$(.CURDIR)/../../../include

.include "test.mk"

.include <mkc.prog.mk>
