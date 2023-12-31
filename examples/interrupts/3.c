/* Copyright (c) 2016,2017,2018 Runtime Verification, Inc.
 * All rights reserved.
 */
#include <err.h>
#include <libgen.h>
#include <signal.h>
#include <stdbool.h>
#include <stdlib.h>
#include <unistd.h>

#include "lib.h"
#include "nbcompat.h"

/* Interrupt data-race category 3:
 * 
 * A low-priority thread is interrupted during a store on a shared
 * memory location, L; an interrupt handler performs a store that
 * overlaps L; the shared memory region potentially takes a value that
 * is different than either the thread or the interrupt handler stored.
 */

struct {
	int count;
} shared = {.count = 0};

static void
handler(int signum __unused)
{
	shared.count = 10;
}

int
main(int argc __unused, char **argv)
{
	int i;
	sigset_t oldset;

	pthread_sigmask(SIG_SETMASK, NULL, &oldset);
	establish(handler, basename(argv[0])[0] == 'r');

	for (i = 0; i < 10; i++) {
		shared.count = i;
		pause();
	}

	pthread_sigmask(SIG_SETMASK, &oldset, NULL);
	return EXIT_SUCCESS;
}
