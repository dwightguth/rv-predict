/* Copyright (c) 2016,2017,2018 Runtime Verification, Inc.
 * All rights reserved.
 */
#include <err.h>
#include <libgen.h>
#include <signal.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "lib.h"
#include "nbcompat.h"

/* Interrupt data-race category 2:
 *
 * A low-priority thread is interrupted during a store on a shared
 * memory location, L; the interrupt handler performs a load that
 * overlaps L; the interrupt handler potentially observes a value that
 * is different both from the original value and from the value the
 * low-priority thread was storing.
 */

struct {
	_Atomic bool protected;
	int count;
} shared = {.protected = false, .count = 0};

static void
handler(int signum __unused)
{
	const char msg[] = "shared.count == 10\n";

	if (shared.protected)
		return;

	if (shared.count == 10)
		(void)write(STDOUT_FILENO, msg, strlen(msg));
}

int
main(int argc __unused, char **argv)
{
	int i;
	sigset_t oldset;

	pthread_sigmask(SIG_SETMASK, NULL, &oldset);
	establish(handler, basename(argv[0])[0] == 'r');

	for (i = 1; i <= 10; i++) {
		shared.protected = true;
		shared.count = i;
		shared.protected = false;
		pause();
	}

	pthread_sigmask(SIG_SETMASK, &oldset, NULL);
	return EXIT_SUCCESS;
}
