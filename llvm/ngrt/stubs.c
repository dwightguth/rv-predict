#include <err.h>
#include <stdlib.h>

#include "nbcompat.h"
#include "supervise.h"

void
rvp_offline_analysis_start(void)
{
	warnx("offline analysis is not supported on this platform.");
	errx(EXIT_FAILURE,
	    "In the environment, set RVP_TRACE_ONLY to \"yes\".");
}

void
rvp_online_analysis_start(void)
{
	warnx("online analysis is not supported on this platform.");
	errx(EXIT_FAILURE,
	    "In the environment, set RVP_OFFLINE_ANALYSIS to \"no\".");
}
