/* Copyright (c) 2017 Runtime Verification, Inc.  All rights reserved. */

#include "init.h"
#include "aligned.h"
#include "atomic.h"
#include "cas.h"
#include "exchange.h"
#include "notimpl.h"
#include "ring.h"
#include "thread.h"
#include "unaligned.h"

extern const char __rvpredict_cov_begin;
extern const char __rvpredict_cov_end;

/* Return true if we should not trace this variable because
 * it belongs to the LLVM coverage runtime.  Otherwise, return false.
 */
static inline bool
data_is_in_coverage(rvp_addr_t addr)
{
	return (uintptr_t)&__rvpredict_cov_begin <= (uintptr_t)addr &&
	       (uintptr_t)addr <= (uintptr_t)&__rvpredict_cov_end;
}

static inline void
trace_load(const char *retaddr, rvp_op_t op, rvp_addr_t addr, uint32_t val)
{
	if (__predict_false(data_is_in_coverage(addr) || !ring_operational()))
		return;

	rvp_ring_t *r = rvp_ring_for_curthr();
	/* _cursor_for_ring() ensures there is room for an rvp_buf_t's
	 * worth of traces.
	 */
	rvp_cursor_t c = rvp_cursor_for_ring(r);

	rvp_cursor_trace_load_cog(&c, &r->r_lgen);
	rvp_cursor_put_pc_and_op(&c, &r->r_lastpc, retaddr, op);
	rvp_cursor_put_addr(&c, addr);
	rvp_cursor_put(&c, val);
	rvp_ring_advance_to_cursor(r, &c);
}

static inline void
trace_load8(const char *retaddr, rvp_op_t op, rvp_addr_t addr, uint64_t val)
{
	if (__predict_false(data_is_in_coverage(addr) || !ring_operational()))
		return;

	rvp_ring_t *r = rvp_ring_for_curthr();
	rvp_cursor_t c = rvp_cursor_for_ring(r);

	rvp_cursor_trace_load_cog(&c, &r->r_lgen);
	rvp_cursor_put_pc_and_op(&c, &r->r_lastpc, retaddr, op);
	rvp_cursor_put_addr(&c, addr);
	rvp_cursor_put_u64(&c, val);
	rvp_ring_advance_to_cursor(r, &c);
}

static inline void
trace_store(const char *retaddr, rvp_op_t op, rvp_addr_t addr, uint32_t val)
{
	if (__predict_false(data_is_in_coverage(addr) || !ring_operational()))
		return;

	rvp_ring_t *r = rvp_ring_for_curthr();
	uint64_t gen;

	gen = rvp_ggen_before_store();
	atomic_thread_fence(memory_order_acquire);
	rvp_cursor_t c = rvp_cursor_for_ring(r);
	rvp_cursor_put_pc_and_op(&c, &r->r_lastpc, retaddr, op);
	rvp_cursor_put_addr(&c, addr);
	rvp_cursor_put(&c, val);
	rvp_cursor_trace_cog(&c, &r->r_lgen, gen);
	rvp_ring_advance_to_cursor(r, &c);
}

static inline unsigned int
decade(unsigned int n)
{
	return n & ~0x1ffU;
}

static inline void
trace_store8(rvp_ring_t *r, const char *retaddr, rvp_op_t op, rvp_addr_t addr,
    uint64_t val)
{
	unsigned int nempty;
	const int slots_per_store =
	    sizeof(rvp_load8_store8_t) / sizeof(*r->r_producer);

	if (__predict_false(data_is_in_coverage(addr) || !ring_operational()))
		return;

	uint64_t gen = rvp_ggen_before_store();
	atomic_thread_fence(memory_order_acquire);
	deltop_t *deltop = rvp_vec_and_op_to_deltop(retaddr - r->r_lastpc, op);
	uint32_t *producer = r->r_producer;
	if (__predict_true(deltop != NULL &&
	    r->r_lgen >= gen &&
	    producer <= &r->r_items[RVP_RING_ITEMS - slots_per_store] &&
	    (nempty = rvp_ring_nempty_for_producer(r, producer)) >= slots_per_store)) {
		rvp_load8_store8_t *store = (rvp_load8_store8_t *)producer;
		store->deltop = (rvp_addr_t)deltop;
		store->addr = addr;
		store->data = val;
		atomic_store_explicit(&r->r_producer,
		    producer + slots_per_store, memory_order_release);
		r->r_lastpc = retaddr;
		if (__predict_false(decade(nempty) !=
		                    decade(nempty - slots_per_store)))
			rvp_increase_ggen();
		/* If the number of empty slots just crossed from above the
		 * service threshold to below, then ask the serialization
		 * thread to service the ring.
		 */
		if (__predict_false(nempty >= RVP_RING_SERVICE_THRESHOLD &&
		    RVP_RING_SERVICE_THRESHOLD > nempty - slots_per_store))
			rvp_ring_request_service(r);
	} else {
		nempty = rvp_ring_nempty(r);
		bool do_increase =
		    decade(nempty) != decade(nempty - slots_per_store) &&
		    r->r_lgen >= gen;
		rvp_cursor_t c = rvp_cursor_for_ring(r);
		rvp_cursor_put_pc_and_op(&c, &r->r_lastpc, retaddr, op);
		rvp_cursor_put_addr(&c, addr);
		rvp_cursor_put_u64(&c, val);
		rvp_cursor_trace_cog(&c, &r->r_lgen, gen);
		rvp_ring_advance_to_cursor(r, &c);
		if (do_increase)
			rvp_increase_ggen();
	}
}
