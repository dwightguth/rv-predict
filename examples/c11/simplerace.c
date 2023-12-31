/* Copyright (c) 2016,2017,2018 Runtime Verification, Inc.
 * All rights reserved.
 */
#include <stdio.h>
#include <stdlib.h>	/* for EXIT_SUCCESS */
#include <pthread.h>

#include "nbcompat.h"	/* for __unused */

int shared_var = 1;

static void *
thread1(void *arg __unused)
{
	shared_var++;
	return NULL;
}

static void *
thread2(void *arg __unused)
{
	shared_var++;
	return NULL;
}

int
main(void)
{
	pthread_t t1, t2;

	pthread_create(&t1, NULL, thread1, NULL);
	pthread_create(&t2, NULL, thread2, NULL);
	pthread_join(t1, NULL);
	pthread_join(t2, NULL);
	printf("shared_var = %d\n", shared_var);
	return EXIT_SUCCESS;
}
