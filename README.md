#Short Description

RV-Predict is the only dynamic data race detector that is both sound and 
maximal. 
*Dynamic* means that it executes the program in order to extract an execution 
trace to analyze. *Sound* means that it only reports races which are real (i.e., 
no false positives). And *maximal* means that it finds all the races that can be 
found by any other sound race detector analyzing the same execution trace. The 
technology underlying RV-Predict is best explained in this
[PLDI'14 paper](http://dx.doi.org/10.1145/2594291.2594315). 

# Prerequisites

RV-Predict requires Java Runtime Environment 1.7 or higher. 

Moreover, RV-Predict relies on an SMT solver for solving constraints.  Please
download and install [Z3](http://z3.codeplex.com) prior to installing
RV-Predict.  Although mostly tested with Z3, RV-Predict also supports
the SMT-LIB v1.2 language (currently only through Yices).  Please see
the options below.

# Installation

Download the installer from
[RV-Predict website](http://runtimeverification.com/predict/rv-predict-install.jar)
and execute 

    java -jar rv-predict-install.jar 
Then follow the installation instructions.  Remember to add the `bin` directory 
under the RV-Predict installation directory to your `PATH` environment variable.

# Running RV-Predict

RV-Predict is designed as a drop-off replacement for the `java` command
line.  It is invoked with `rv-predict.bat` on Windows, and `rv-predict`
on Linux and UNIX platforms.

## Basic Usage

Invoke `rv-predict` on a class as you would invoke the Java interpreter:

    rv-predict [options] class [args...]        #(to predict races in a class), or
    rv-predict [options] -jar jarfile [args...] #(to predict races in an executable jar)
where `[options]` include both RV-Predict and Java specific options.

### Example

Running command:

    rv-predict -cp benchmarks\bin account.Account
Standard output:

    Bank system started
    loop: 2
    loop: 2
    sum: -174
    sum: 256
    sum: -33
    sum: 76
    ..
    End of the week.
    Bank records = 125, accounts balance = 125.
    Records match.

Standard error:

    Race: account.Account|go([Ljava.lang.String;)V|account.BankAccount.Balance|67 - account.Account|Service(II)V|account.BankAccount.Balance|97
    Race: account.Account|Service(II)V|account.Account.Bank_Total|98 - account.Account|Service(II)V|account.Account.Bank_Total|98
    Race: account.Account|checkResult(I)V|account.Account.Bank_Total|75 - account.Account|Service(II)V|account.Account.Bank_Total|98
    Race: account.Account|checkResult(I)V|account.Account.Bank_Total|76 - account.Account|Service(II)V|account.Account.Bank_Total|98

## Interpreting the results

Upon invoking RV-Predict on a class or a jar file, one should expect a normal
execution of the class/jar (albeit slower, as the execution is logged),
followed by a list of races (if any) that were discovered during the execution.
Although some races might be benign for a particular program, all reported
races could actually occur under a different thread interleaving.  Benign
races can become problematic when the memory model or the platform changes,
so it is good practice to eliminate them from your code anyway.

For the example above, the `Account` example is executed, and what we observe 
in the standard output stream is a normal interaction which exhibits no 
data race, also indicated by the fact that the records match at the end of 
the session.

The standard error stream output shows the results of the analysis performed 
on the logged trace which exhibits 4 possible violations which could have 
occurred if the thread scheduling would have been different.

A race description is introduced by the `Race: ` keyword, followed by the 
two strings identifying the locations in race, separated by ` - `. 
A location descriptor consists of 4 components separated by `|`.
These components are:

- `account.Account` — the fully qualified name of the class where the
conflict occurred
- `go([Ljava.lang.String;)V` — the signature of the method in which the 
conflict occurred 
- `account.BankAccount.Balance` — the fully qualified name of the field 
involved in the race
- `67` — the line number for this location

Thus, the first race description can be read as follows:
> There is a race between the `Balance` field of the `BankAccount`
> class accessed in method `go` of the `Account` class at line `67` 
> and the access of the same field in method `Service` of class 
> `Account` at line `97`.

If no races are found, then the message `No races found.` is appended to the
standard output stream.

## Fine Tuning the Execution

Although the basic usage should be sufficient for most scenarios, 
RV-Predict provides several options to allow advanced users to tune 
the execution, analysis, and the produced output.

### Common options

The list of common options can be obtained by using the `-h` or `--help` 
option when invoking RV-Predict:
 		
    rv-predict --help
    Usage: rv-predict [rv_predict_options] [java_options] <command_line>
        Common options (use -h -v for a complete list):

    -h, --help              print help info
        --java              optional separator for Java arguments
        --log                record execution in given directory (no prediction)
        --predict            run prediction on logs from given directory
        --timeout           rv-predict timeout in seconds 
                            Default: 3600
    -v, --verbose           generate more verbose output


- the `--log` option can used to tell RV-Predict that the execution should be
logged in the given directory and that the prediction phase should be skipped.
- the `--predict` option can used to tell RV-Predict to skip the logging phase,
using the logged trace in the given directory to run the prediction algorithms 
on. When using this option specifying the java command is no longer necessary.
- the `--timeout` option controls the total execution time we allow for the 
prediction phase.
- the `--java` option can be used as the final RV-specific option, to separate
the RV parameters from the Java ones.  This is especially useful if the command 
line of the program being run uses arguments with the same syntax as 
the RV-Predict ones.

### Advanced options

The complete list of RV-Predict options can be obtained by
combining the `-h` and `-v` options when invoking RV-Predict:

    rv-predict -h -v

As this list is always evolving, we refrain from listing all these 
options here.  However, we would like to mention `--smtlib1` which instructs
RV-Predict to format SMT queries in the SMT-LIB v1.2 language and use Yices 
(`yices-smt`) to check them.

----------
Additional online documentation can be found on the 
[RV-Predict website](http://runtimeverification.com/predict)
