/* Copyright (c) 2018 Runtime Verification, Inc.  All rights reserved. */

/* Stopgap implementaiton of fork.
 *
 * XXX This is a stopgap fix for the hang that used to occur in a child
 * XXX process once it filled a ring.  Because a child process had
 * XXX no serialization thread, it would wait forever for its ring
 * XXX to empty.  Now, a child process's rings are just emptied to
 * XXX /dev/null, so you cannot find any races in child processes.
 */

#include <assert.h>
#include <err.h> /* for err(3) */
#include <errno.h> /* for ESRCH */
#include <inttypes.h> /* for PRIu32 */
#include <signal.h> /* for pthread_sigmask */
#include <stdatomic.h> /* for atomic_is_lock_free */
#include <stdint.h> /* for uint32_t */
#include <stdlib.h> /* for EXIT_FAILURE */
#include <stdio.h> /* for fprintf(3) */
#include <string.h> /* for strerror(3) */
#include <unistd.h> /* for fork(2) */

#include <sys/types.h>	/* for open(2) */
#include <sys/stat.h>	/* for open(2) */
#include <fcntl.h>	/* for open(2) */

#include "init.h"
#include "interpose.h"
#include "relay.h"
#include "serialize.h"

REAL_DEFN(pid_t, fork, void);

void
rvp_fork_init(void)
{
	ESTABLISH_PTR_TO_REAL(pid_t (*)(void), fork);
}

pid_t
__rvpredict_fork(void)
{
	pid_t rc = real_fork();

	if (rc == 0) {
		int fd = open("/dev/null", O_WRONLY);
		if (fd == -1)
			abort();
		rvp_relay_create();
		rvp_serializer_create(fd);
	}
	return rc;
}

INTERPOSE(pid_t, fork, void);
