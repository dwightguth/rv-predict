#include <err.h>
#include <errno.h>
#include <limits.h>	/* for SSIZE_MAX */
#include <signal.h>	/* for pthread_sigmask(3) */
#include <stdio.h>
#include <stdarg.h>	/* for vfprintf(3) */
#include <stdint.h>	/* for intmax_t */
#include <stdlib.h>	/* for STDIN_FILENO */
#include <string.h>	/* for strdup(3), strlen(3) */
#include <sys/param.h>	/* for MIN */
#include <sys/stat.h>	/* for lstat(2), fts(3) */
#include <sys/types.h>	/* for waitpid(2), fts(3) */
#include <fts.h>
#include <sys/wait.h>
#include <unistd.h>	/* for pathconf(2), pipe(2), readlink(2) */

#include "interpose.h"	/* for real_pthread_sigmask */
#include "nbcompat.h"
#include "supervise.h"

static int __printflike(1, 2)
dbg_printf(const char *fmt, ...)
{
	va_list ap;
	int rc;

	if (!rvp_debug_supervisor)
		return 0;

	va_start(ap, fmt);
	rc = vfprintf(stderr, fmt, ap);
	va_end(ap);
	return rc;
}

static void
sigchld(int signo __unused)
{
	return;
}

static void
prepare_to_wait_for_analysis(struct sigaction **actionp, sigset_t *setp)
{
	struct sigaction sa = {.sa_handler = sigchld};

	if (sigemptyset(&sa.sa_mask) == -1)
		err(EXIT_FAILURE, "%s.%d: sigemptyset", __func__, __LINE__);

	if (sigemptyset(setp) == -1)
		err(EXIT_FAILURE, "%s.%d: sigemptyset", __func__, __LINE__);

	if (sigaddset_killers(setp) == -1) {
		err(EXIT_FAILURE, "%s.%d: sigaddset_killers", __func__,
		    __LINE__);
	}

	if (sigaddset(setp, SIGCHLD) == -1)
		err(EXIT_FAILURE, "%s.%d: sigaddset", __func__, __LINE__);

	if (real_pthread_sigmask(SIG_BLOCK, setp, NULL) == -1)
		err(EXIT_FAILURE, "%s: pthread_sigmask", __func__);
	if (real_sigaction(SIGCHLD, &sa, NULL) == -1)
		err(EXIT_FAILURE, "%s: sigaction", __func__);

	reset_signals(actionp);
}

void
rvp_online_analysis_start(void)
{
	struct sigaction *action;
	int astatus, sstatus;
	pid_t supervisee_pid, analysis_pid, parent_pid;
	int pipefd[2];
	sigset_t waitset;
	pid_t quit_pid;
	int signo;

	char *const binpath =
	    get_binary_path(/* (argc > 0) ? argv[0] : NULL */);

	if (binpath == NULL) {
		errx(EXIT_FAILURE, "%s could not find a path to the "
		    "executable binary that it was running", product_name);
	}

	if (pipe(pipefd) == -1) {
		err(EXIT_FAILURE,
		    "%s could not create an event pipeline", product_name);
	}
	const int piperd = pipefd[0];
	const int pipewr = pipefd[1];

	// TBD Avoid using some arbitrary file in the analysis.
	// Check binpath for an RV-Predict/C runtime symbol, and see if
	// its address matches the address of our own copy?

	if ((supervisee_pid = real_fork()) == -1) {
		err(EXIT_FAILURE,
		    "%s could not fork a supervisee process", product_name);
	}

	ignore_signals(&action);

	/* the child process runs the program: return to its main routine */
	if (supervisee_pid == 0) {
		rvp_analysis_fd = pipewr;
		(void)close(piperd);
		reset_signals(&action);
		return;
	}

	if (close(pipewr) == -1) {
		err(EXIT_FAILURE,
		    "%s could not close event pipeline inlet", product_name);
	}

	analysis_pid = real_fork();

	if (analysis_pid == 0) {
		char cmdname[] = "rvpa", tracename[] = "/dev/stdin";
		char *const args[] = {cmdname, binpath, tracename, NULL};

		/* We move the analyzer into its own process group
		 * so that signals generated on the terminal (e.g.,
		 * Control-C -> SIGINT) are not delivered to it.
		 */
		if (setpgid(0, 0) == -1) {
			err(EXIT_FAILURE,
			    "%s could not put the analyzer into its own "
			    "process group", product_name);
		}

		reset_signals(&action);

		/* establish read end of pipe as rvpa's stdin */
		if (dup2(piperd, STDIN_FILENO) == -1) {
			err(EXIT_FAILURE, "%s could not establish "
			    "the event pipeline as the analysis input",
			    product_name);
		}
		(void)close(piperd);

		if (execvp("rvpa", args) == -1) {
			err(EXIT_FAILURE,
			    "%s could not start an analysis process",
			    product_name);
		}
		// unreachable
	}

	if (close(piperd) == -1) {
		err(EXIT_FAILURE,
		    "%s could not close event pipeline outlet", product_name);
	}

	/* wait for the supervisee to finish */
	while (waitpid(supervisee_pid, &sstatus, WUNTRACED) == -1) {
		if (errno == EINTR)
			continue;
		err(EXIT_FAILURE, "%s failed unexpectedly while it "
		    "waited for the instrumented program",
		    product_name);
	}

	parent_pid = getppid();

	dbg_printf("%s.%d: supervisee finished, parent PID %d\n",
	    __func__, __LINE__, parent_pid);

	/* Now that the supervisee (the program under test) has
	 * finished, the supervisor blocks the signals in the
	 * `killer_signum` set, reestablishes default signal disposition,
	 * and then waits either for a signal in `killer_signum` to arrive,
	 * or for SIGCHLD.
	 *
	 * When signals in `killer_signum` arrive, the supervisor forwards
	 * them to the analysis process and continues waiting.
	 *
	 * When SIGCHLD arrives, the supervisor checks to see if the
	 * analysis process has finished.
	 *
	 * It is possible for the analysis process to finish during the
	 * interval when SIGCHLD is ignored, in which case the supervisor
	 * will never receive a SIGCHLD for the analysis.  So the supervisor
	 * checks whether or not the analysis process still runs before it
	 * waits for signals.
	 */

	prepare_to_wait_for_analysis(&action, &waitset);
	if (parent_pid == 1) {
		if (WIFSIGNALED(sstatus)) {
			signo = WTERMSIG(sstatus);
			dbg_printf(
			    "%s belongs to init, forwarding %s to analysis\n",
			    product_name, strsignal(signo));
		} else {
			signo = SIGHUP;
			dbg_printf(
			    "%s belongs to init, killing analysis with %s\n",
			    product_name, strsignal(signo));
		}
		if (kill(-analysis_pid, signo) == -1) {
			err(EXIT_FAILURE, "%s.%d: kill",
			    __func__, __LINE__);
		}
	}

	if (analysis_pid == -1) {
		fprintf(stderr, "%s could not start the analysis process.\n",
		    product_name);
		goto supervisee_report;
	} else if ((quit_pid = waitpid(analysis_pid, &astatus, WNOHANG)) == -1) {
		err(EXIT_FAILURE, "%s: waitpid(analyzer)",
		    product_name);
	} else if (quit_pid == analysis_pid) {
		;
	} else while (sigwait(&waitset, &signo) == 0) {
		dbg_printf("%s: got signal %d (%s)\n", product_name,
		    signo, strsignal(signo));
		if (signo != SIGCHLD) {
			if (kill(-analysis_pid, signo) == -1) {
				err(EXIT_FAILURE, "%s.%d: kill",
				    __func__, __LINE__);
			}
			dbg_printf("%s: forwarded signal %d\n",
			    product_name, signo);
			continue;
		}
		/* wait for the analysis to finish */
		if (waitpid(analysis_pid, &astatus, 0) == -1) {
			err(EXIT_FAILURE,
			    "%s failed unexpectedly while it waited for the "
			    "analyzer to finish",
			    product_name);
		}
		break;
	}

	dbg_printf("%s.%d: analyzer finished\n", __func__, __LINE__);

	/* Print the status of the analyzer if it was cancelled
	 * by a signal.
	 */
	if (WIFSIGNALED(astatus)) {
		fprintf(stderr, "analyzer: %s", strsignal(WTERMSIG(astatus)));
#ifdef WCOREDUMP
		if (WCOREDUMP(astatus))
			fprintf(stderr, " (core dumped)");
#endif /* WCOREDUMP */
		fputc('\n', stderr);
	}

supervisee_report:

	/* Print the status of the supervisee if it was cancelled
	 * by a signal.
	 */
	if (WIFSIGNALED(sstatus)) {
		fprintf(stderr, "%s", strsignal(WTERMSIG(sstatus)));
#ifdef WCOREDUMP
		if (WCOREDUMP(sstatus))
			fprintf(stderr, " (core dumped)");
#endif /* WCOREDUMP */
		fputc('\n', stderr);
	}

	if (WIFSIGNALED(sstatus))
		exit(125);	// following xargs(1) here. :-)
	if (WIFEXITED(sstatus))
		exit(WEXITSTATUS(sstatus));
	exit(EXIT_SUCCESS);
}
