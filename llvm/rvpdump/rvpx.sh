#!/bin/sh

set -e

tmpdir=$(mktemp -d)
exitcode=1

exit_hook()
{
	for core in $(ls $tmpdir/*core); do
		echo "$(basename $0): there are cores in $tmpdir/." 1>&2
		exit $exitcode
	done
        rm -rf $tmpdir
        exit $exitcode
}

# Suppress "$ " output, which seems to be caused by "set -i" and "set +i".
PS1=""

set -i
trap exit_hook EXIT ALRM HUP INT PIPE QUIT TERM
set +i

if [ ${RV_PREDICT_HOME:-x} = x ]; then
	if [ ${RV_ROOT:-x} = x ]; then
		echo "Neither RV_PREDICT_HOME nor RV_ROOT is set." 1>&2
	else
		export RV_PREDICT_HOME=${RV_ROOT}/rv-predict
	fi
	exit 1
fi

export RVP_TRACE_FILE=${tmpdir}/rvpredict.trace

progname=$1
if [ ${progname##/} != ${progname} ]; then
	progpath=${progname}
else
	progpath=$(pwd)/${progname}
fi

set +e
"$@"
exitcode=$!
set -e

cd $tmpdir
$RV_PREDICT_HOME/llvm/rvpdump/rvpdump -t legacy rvpredict.trace
rv-predict --offline --window 4000 --llvm-predict . 2>&1 | \
rvpsymbolize $progpath 1>&2

trap - EXIT ALRM HUP INT PIPE QUIT TERM

rm -rf $tmpdir

exit $exitcode