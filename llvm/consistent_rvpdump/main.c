#include <sys/types.h>	/* for open(2) */
#include <sys/stat.h>	/* for open(2) */
#include <err.h>
#include <errno.h>
#include <fcntl.h>	/* for open(2) */
#include <inttypes.h>	/* for intmax_t */
#include <limits.h>	/* for SIZE_MAX */
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>	/* for EXIT_* */
#include <string.h>	/* strcmp(3) */
#include <unistd.h>	/* for STDIN_FILENO */

#include "nbcompat.h"
#include "reader.h"

static void __dead
usage(const char *progname)
{
	fprintf(stderr,
	    "usage: %s [-g] "
	    "[-t <binary|plain|symbol-friendly>] [-n #traces]\n"
	    "[<trace file>]\n", progname);
	exit(EXIT_FAILURE);
}

int
main(int argc, char **argv)
{
	int ch, fd;
	const char *inputname;
	const char *progname = argv[0];
	rvp_output_params_t op = {
	  .op_type = RVP_OUTPUT_PLAIN_TEXT
	, .op_emit_generation = false
	, .op_nrecords = SIZE_MAX
	};
	intmax_t tmpn;
	char *end;

	while ((ch = getopt(argc, argv, "gn:t:")) != -1) {
		switch (ch) {
		case 'n':
			errno = 0;
			tmpn = strtoimax(optarg, &end, 10);
			if (errno != 0) {
				err(EXIT_FAILURE, "could not parse -n %s",
				    optarg);
			}
			if (end == optarg) {
				errx(EXIT_FAILURE, "no numeric characters "
				    "in -n %s", optarg);
			}
			if (*end != '\0') {
				errx(EXIT_FAILURE, "extraneous characters "
				    "after -n %jd", tmpn);
			}
			if (tmpn < 0 || SIZE_MAX < tmpn)
				errx(EXIT_FAILURE, "-n %jd: out range", tmpn);
			op.op_nrecords = tmpn;
			break;
		case 'g':
			op.op_emit_generation = true;
			break;
		case 't':
			if (strcmp(optarg, "binary") == 0)
				op.op_type = RVP_OUTPUT_BINARY;
			else if (strcmp(optarg, "plain") == 0)
				op.op_type = RVP_OUTPUT_PLAIN_TEXT;
			else if (strcmp(optarg, "symbol-friendly") == 0)
				op.op_type = RVP_OUTPUT_SYMBOL_FRIENDLY;
			else
				usage(progname);
			break;
		default: /* '?' */
			usage(progname);
		}
	}

	argc -= optind;
	argv += optind;
 
	if (argc > 1) {
		usage(progname);
	} else if (argc == 1) {
		inputname = argv[0];
		fd = open(inputname, O_RDONLY);
		if (fd == -1) {
			err(EXIT_FAILURE, "%s: open(\"%s\")",
			    __func__, inputname);
		}
	} else {
		fd = STDIN_FILENO;
		inputname = "<stdin>";
	}

	rvp_trace_dump(&op, fd);

	return EXIT_SUCCESS;
}
