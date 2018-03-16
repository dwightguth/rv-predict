#include <signal.h>
#include <stdbool.h>
#include <stdlib.h>
#include <unistd.h>

#include "nbcompat.h"

#define update(__lvalue) do {	\
	(__lvalue) ^= 1;	\
} while (/*CONSTCOND*/false)

static int racy, racefree;

static void __rv_interrupt_handler(irq0, 0)
lopri_handler(void)
{
	const char enter_msg[] = "( low priority interrupt\n";
	const char exit_msg[] = "low priority interrupt )\n";

	write(STDERR_FILENO, enter_msg, sizeof(enter_msg) - 1);

	update(racefree);
	EI();
	update(racy);
	write(STDERR_FILENO, exit_msg, sizeof(exit_msg) - 1);
}

static void __rv_interrupt_handler(irq1, 1)
hipri_handler(void)
{
	const char enter_msg[] = "( high priority interrupt\n";
	const char exit_msg[] = "high priority interrupt )\n";

	write(STDERR_FILENO, enter_msg, sizeof(enter_msg) - 1);

	update(racefree);
	EI();
	update(racy);
	DI();
	update(racefree);
	write(STDERR_FILENO, exit_msg, sizeof(exit_msg) - 1);
}

int
main(void)
{
	EI();
	update(racy);
	DI();
	update(racefree);
	EI();
	update(racy);
	DI();
	update(racefree);
	EI();
	update(racy);
	DI();
	return EXIT_SUCCESS;
}
