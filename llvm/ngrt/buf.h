#ifndef _RVP_BUF_H_
#define _RVP_BUF_H_

#include <assert.h>
#include <stdint.h>	/* for uint32_t */

#include "nbcompat.h"	/* for __arraycount() */
#include "tracefmt.h"	/* for rvp_op_t */

typedef struct {
	unsigned int b_nwords;
	uint32_t b_word[20];
} rvp_buf_t;

#define RVP_BUF_INITIALIZER	(rvp_buf_t){ .b_nwords = 0 }

inline void
rvp_buf_put(rvp_buf_t *b, uint32_t item)
{
	assert(b->b_nwords < __arraycount(b->b_word));
	b->b_word[b->b_nwords++] = item;
}

static inline void
rvp_buf_put_buf(rvp_buf_t *db, const rvp_buf_t *sb)
{
	unsigned int i;

	assert(db->b_nwords + sb->b_nwords <= __arraycount(db->b_word));
	for (i = 0; i < sb->b_nwords; i++)
		db->b_word[db->b_nwords++] = sb->b_word[i];
}

inline void
rvp_buf_put_addr(rvp_buf_t *b, rvp_addr_t addr)
{
	unsigned int i;
	union {
		rvp_addr_t uaddr;
		uint32_t u32[sizeof(rvp_addr_t) / sizeof(uint32_t)];
	} addru = {.uaddr = addr};

	for (i = 0; i < __arraycount(addru.u32); i++) {
		rvp_buf_put(b, addru.u32[i]);
	}
}

static inline void
rvp_buf_put_voidptr(rvp_buf_t *b, const void *addr)
{
	rvp_buf_put_addr(b, (rvp_addr_t)addr);
}

inline void
rvp_buf_put_u64(rvp_buf_t *b, uint64_t val)
{
	union {
		uint64_t u64;
		uint32_t u32[2];
	} valu = {.u64 = val};

	rvp_buf_put(b, valu.u32[0]);
	rvp_buf_put(b, valu.u32[1]);
}

void rvp_buf_put_pc_and_op(rvp_buf_t *, const char **, const char *, rvp_op_t);
void rvp_buf_put_cog(rvp_buf_t *, uint64_t);

#endif /* _RVP_BUF_H_ */
