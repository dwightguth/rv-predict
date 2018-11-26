#include <err.h>
#include <errno.h>
#include <pthread.h>
#include <sched.h>	/* for sched_yield(2) */
#include <stdio.h>	/* for stderr, fprintf(3) */
#include <stdlib.h>

#include "lock.h"
#include "rvpsignal.h"
#include "supervise.h"	/* for product_name */
#include "thread.h"
#include "trace.h"	/* for rvp_vec_and_op_to_deltop() */

void
rvp_rings_init(void)
{
	long pgsz;

	if ((pgsz = sysconf(_SC_PAGE_SIZE)) == -1)
		err(EXIT_FAILURE, "%s: sysconf", __func__);
	if (RVP_RING_BYTES % pgsz != 0) {
		errx(EXIT_FAILURE,
		    "System page size does not evenly divide %s ring size.",
		    product_name);
	}
}

static void
rvp_iring_init(rvp_iring_t *ir)
{
	ir->ir_producer = ir->ir_consumer = &ir->ir_items[0];
}

void
rvp_ring_init(rvp_ring_t *r, uint32_t *items, size_t nitems)
{
	int rc;

	r->r_producer = r->r_consumer = items;
#if !defined(EMBED_RING)
	r->r_items = items;
#endif
	r->r_last = &r->r_items[nitems - 1];
	r->r_state = RVP_RING_S_INUSE;
	r->r_mtxp = NULL;
	if ((rc = real_pthread_mutex_init(&r->r_mtx, NULL)) != 0) {
		errx(EXIT_FAILURE, "%s: pthread_mutex_init: %s", __func__,
		    strerror(rc));
	}
	if ((rc = pthread_cond_init(&r->r_cv, NULL)) != 0) {
		errx(EXIT_FAILURE, "%s: pthread_cond_init: %s", __func__,
		    strerror(rc));
	}
	rvp_iring_init(&r->r_iring);
}

int
rvp_ring_stdinit(rvp_ring_t *r)
{
#if defined(EMBED_RING)
	rvp_ring_init(r, &r->r_items[0], RVP_RING_ITEMS);
#else
	uint32_t *items;

	items = calloc(RVP_RING_ITEMS, sizeof(*r->r_items));
	if (items == NULL)
		return ENOMEM;

	rvp_ring_init(r, items, RVP_RING_ITEMS);
#endif

	return 0;
}

/* r->r_idepth must equal 0. */
void
rvp_ring_in_thread_wait_for_nempty(rvp_ring_t *r, int nempty)
{
	int ostate, discard;

	/* I disable cancellation here to ensure that the ring
	 * mutex is released.  Application threads run this routine,
	 * and all application threads are susceptible to cancellation.
	 */
	pthread_setcancelstate(PTHREAD_CANCEL_DISABLE, &ostate);
	real_pthread_mutex_lock(&r->r_mtx);
	atomic_store_explicit(&r->r_mtxp, &r->r_mtx, memory_order_release);

	r->r_stats->rs_ring_waits++;

	/* Check again now that we hold the lock.  Skip the wakeup
	 * and wait if enough slots emptied in the mean time.
	 */
	while (rvp_ring_nempty(r) < nempty) {
		rvp_wake_transmitter();
		r->r_stats->rs_ring_sleeps++;
		pthread_cond_wait(&r->r_cv, &r->r_mtx);
	}
	r->r_mtxp = NULL;
	real_pthread_mutex_unlock(&r->r_mtx);
	pthread_setcancelstate(ostate, &discard);
}

void
rvp_ring_in_signal_wait_for_nempty(rvp_ring_t *r, int nempty)
{
	int i;
	volatile int j;

	r->r_stats->rs_ring_waits++;

	for (i = 32; rvp_ring_nempty(r) < nempty; i = MIN(16384, i + 1)) {
		for (j = 0; j < i; j++)
			;
		r->r_stats->rs_ring_spins += i;
	}
}

rvp_interruption_t *
rvp_ring_put_interruption(rvp_ring_t *r, rvp_ring_t *interruptor, int sidx)
{
	rvp_iring_t *ir = &r->r_iring;
	rvp_interruption_t *prev = ir->ir_producer;
	rvp_interruption_t *next =
	    (prev == rvp_iring_last(ir)) ? &ir->ir_items[0] : (prev + 1);

	while (rvp_iring_nempty(ir) < 1)
		rvp_iring_await_one_empty(r);

	prev->it_interruptor = interruptor;
	prev->it_interrupted_idx = r->r_producer - r->r_items;
	prev->it_interruptor_sidx = sidx;
	prev->it_interruptor_eidx = -1;
	atomic_store_explicit(&ir->ir_producer, next, memory_order_release);
	return prev;
}

