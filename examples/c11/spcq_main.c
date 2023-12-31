/* Copyright (c) 2016,2017,2018 Runtime Verification, Inc.
 * All rights reserved.
 */
#include <assert.h>
#include <err.h>
#include <inttypes.h>
#include <limits.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <pthread.h>
#include <unistd.h>
#include "spcq.h"

typedef struct _item {
	int idx;
} item_t;

#ifndef __arraycount
#define __arraycount(__a)	(sizeof(__a) / sizeof((__a)[0]))
#endif

pthread_mutex_t *mutexp = NULL;
item_t *items;

int nitems = 100;

bool timing = false;

static inline void
acquire_queue(void)
{
	int rc;

	if (mutexp == NULL)
		return;

	rc = pthread_mutex_lock(mutexp);
	assert(rc == 0);
}

static inline void
release_queue(void)
{
	int rc;

	if (mutexp == NULL)
		return;

	rc = pthread_mutex_unlock(mutexp);
	assert(rc == 0);
}

static void *
consume(void *arg)
{
	int nread;
	spcq_t *q = arg;
	uint64_t elapsed_ns;
	struct timespec resolution, start, stop;
	const uint32_t ns_per_s = 1000 * 1000 * 1000;
	uint64_t busy_loops = 0;
	const struct timespec half_second = {
		  .tv_sec = 0
		, .tv_nsec = 500 * 1000 * 1000
	};

	nanosleep(&half_second, NULL);

	if (clock_getres(CLOCK_MONOTONIC, &resolution) != 0)
		err(EXIT_FAILURE, "%s: clock_getres", __func__);

	if (clock_gettime(CLOCK_MONOTONIC, &start) != 0)
		err(EXIT_FAILURE, "%s: clock_gettime", __func__);

	for (nread = 0; nread < nitems; nread++) {
		int loops;
		item_t *item;

		for (loops = 0; loops < 10000; loops++) {
			acquire_queue();
			item = spcq_get(q);
			release_queue();
			if (item != NULL) 
				break;
			sched_yield();
		}
		busy_loops += loops;
		printf("read item %d\n", item->idx);
	}

	if (clock_gettime(CLOCK_MONOTONIC, &stop) != 0)
		err(EXIT_FAILURE, "%s: clock_gettime", __func__);

	elapsed_ns = (stop.tv_sec - start.tv_sec) * ns_per_s +
	    stop.tv_nsec - start.tv_nsec;

	if (timing) {
		fprintf(stderr,
		    "%" PRIu64 " ns / %d items, %" PRIu64 " ns resolution\n",
		    elapsed_ns, nitems,
		    resolution.tv_sec * ns_per_s + resolution.tv_nsec);
		fprintf(stderr, "%" PRIu64 " busy loops\n", busy_loops);
	}

	return NULL;
}

static void *
produce(void *arg)
{
	spcq_t *q = arg;
	int i;

	for (i = 0; i < nitems; i++) {
		acquire_queue();
		while (!spcq_put(q, &items[i])) {
			release_queue();
			sched_yield();
			acquire_queue();
		}
		release_queue();
	}

	return NULL;
}

static void
usage(const char *progname)
{
	fprintf(stderr, "Usage: %s [-l]\n", progname);
	exit(EXIT_FAILURE);
}

int
main(int argc, char **argv)
{
	int i, opt;
	spcq_t *q;
	pthread_mutex_t mutex;

	while ((opt = getopt(argc, argv, "ln:t")) != -1) {
		unsigned long v;
		char *end;

		switch (opt) {
		case 'l':
			mutexp = &mutex;
			break;
		case 'n':
			if (*optarg == '-' ||
			    (v = strtoul(optarg, &end, 10)) == ULONG_MAX ||
			    *end != '\0' || v > INT_MAX)
				errx(EXIT_FAILURE, "%s: malformed or out-of-range -n argument", __func__);
			nitems = (int)v;
			break;
		case 't':
			timing = true;
			break;
		default:
			usage(argv[0]);
		}
	}

	if ((items = calloc(nitems, sizeof(items[0]))) == NULL)
		err(EXIT_FAILURE, "%s: calloc", __func__);

	if (pthread_mutex_init(&mutex, NULL) != 0)
		err(EXIT_FAILURE, "%s: pthread_mutex_init", __func__);

	if ((q = spcq_alloc(nitems + 1)) == NULL)
		err(EXIT_FAILURE, "%s: spcq_alloc", __func__);
	pthread_t producer, consumer;
	for (i = 0; i < nitems; i++)
		items[i].idx = i;
	pthread_create(&consumer, NULL, &consume, q);
	pthread_create(&producer, NULL, &produce, q);
	pthread_join(producer, NULL);
	pthread_join(consumer, NULL);
	(void)pthread_mutex_destroy(&mutex);
}
