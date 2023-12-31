#!/bin/sh

set -e
set -u

only_print_command=no
html_dir=
analyze_passthrough=
symbolize_passthrough=
sharedir=$(dirname $0)/../share/rv-predict-c

_usage()
{
	cat 1>&2 <<EOF
usage: $(basename $0) [--window size] [--no-shorten|--no-symbol|--no-trim]
    [--html-dir directory] [--interrupts-target number]
    [--max-interrupt-depth number] [--]
    program [trace-file]
EOF
}

usage()
{
	_usage
	exit 1
}

help()
{
	_usage
	cat 1>&2 <<EOF

For more information, see the manual page, $(basename $0)(1).

EOF
	exit 1
}

rvpredict()
{
	pfx=

	if [ ${only_print_command:-no} = yes ]; then
		pfx=echo
		_java=java
	elif which java >/dev/null; then
		# found java executable in PATH
		_java=java
	elif [ "${JAVA_HOME:-}/bin/java" != "/bin/java" -a \
	       -x "${JAVA_HOME:-}/bin/java" ]; then
		# found java executable in JAVA_HOME
		_java="${JAVA_HOME:-}/bin/java"
	else
		cat 1>&2 <<EOF
RV Predict requires Java ${min_version} to run but Java was not detected.
Please either add it to PATH or set the JAVA_HOME environment variable.
EOF
		exit 2
	fi

	${pfx} ${_java} -ea -jar ${sharedir}/rv-predict.jar "$@"
}

do_output()
{
	[ ${RVP_REPORT_FILE:-none} != none ] \
	    && cat > ${RVP_REPORT_FILE} \
	    || cat 1>&${RVP_REPORT_FD:-2}
}

symbolize()
{
	if [ ${raw:-no} = yes ]; then
		cat
		return
	fi
	rvpsymbolize-json ${symbolize_passthrough} "$@" | \
	{ [ ${filter_trim:-yes} = yes ] && rvptrimframe || cat ; } | \
	{ [ ${filter_shorten:-yes} = yes ] && rvpshortenpaths || cat ; } | \
	RV_ISSUE_REPORT=/dev/stdout \
	    rvp-error ${sharedir}/${output_format:-console}-metadata.json | \
	{ [ x${html_dir:-} != x ] \
	    && rvp-html-report -o ${html_dir} /dev/stdin \
	    || do_output ; }
}

if [ ${RVP_REPORT_FD:-none} != none -a ${RVP_REPORT_FILE:-none} != none ]; then
	echo "$(basename $0): RVP_REPORT_FD conflicts with RVP_REPORT_FILE, set only one." 2>&1
	exit 1
fi

if [ ${RVP_REPORT_FD:-none} != none ]; then
	if [ -n "$(echo -n "$RVP_REPORT_FD" | sed 's/^[0-9]\+$//g')" ]; then
		echo "$(basename $0): malformed RVP_REPORT_FD: expected decimal digits, read '${RVP_REPORT_FD}'" 2>&1
		exit 1
	fi
fi

if [ ${RVP_PROGRESS:-no} = yes ]; then
	set -- "--progress" "$@"
fi

if [ ${RVP_WINDOW_SIZE:-none} != none ]; then
	if [ -n "$(echo -n "$RVP_WINDOW_SIZE" | sed 's/^[0-9]\+$//g')" ]; then
		echo "$(basename $0): malformed RVP_WINDOW_SIZE: expected decimal digits, read '${RVP_WINDOW_SIZE}'" 2>&1
		exit 1
	fi
	set -- "--window" ${RVP_WINDOW_SIZE} "$@"
fi

if [ -n "${RVP_ANALYSIS_ARGS:-}" ]; then
	set -- ${RVP_ANALYSIS_ARGS} "$@"
fi

if [ x${RVP_HTML_DIR:-} != x ]; then
	output_format=json
	html_dir=${RVP_HTML_DIR:-}
fi

while [ $# -ge 1 ]; do
	case $1 in
	-n)
		only_print_command=yes
		shift
		;;
	--no-symbol)
		symbolize_passthrough="${symbolize_passthrough:-} -S"
		shift
		;;
	--no-shorten|--no-trim)
		eval filter_${1##--no-}=no
		shift
		;;
	--html-dir)
		output_format=json
		shift
		html_dir="$1"
		shift
		;;
	--html-dir=*)
		output_format=json
		eval html_dir=${1##--html-dir=}
		shift
		;;
	--output=raw)
		raw=yes
		html_dir=
		shift
		;;
	--output=*)
		html_dir=
		eval output_format=${1##--output=}
		shift
		case $output_format in
		console|csv|json)
			;;
		*)
			echo "$(basename $0): unknown output format "
			    "'${output_format}'" 1>&2
			exit 1
			;;
		esac
		;;
	--interrupt-target=*|--interrupts-target=*)
		interrupts_target=${1##--interrupt-target=}
		interrupts_target=${interrupts_target##--interrupts-target=}
		shift
		;;
	--interrupt-target|--interrupts-target)
		shift
		interrupts_target="--desired-interrupts-per-signal-and-window $1"
		shift
		;;
	--parallel-smt)
		shift
		parallel="--parallel-smt $1"
		shift
		;;
	--window=*)
		window="--window ${1##--window=}"
		shift
		;;
	--window)
		shift
		window="--window $1"
		shift
		;;
	--global-timeout|--solver-timeout|--window-timeout)
		analyze_passthrough="${analyze_passthrough:-} $1 $2"
		shift
		shift
		;;
	--max-interrupt-depth=*)
		analyze_passthrough="${analyze_passthrough:-} --max-interrupt-depth ${1##--max-interrupt-depth=}"
		shift
		;;
	--max-interrupt-depth)
		analyze_passthrough="${analyze_passthrough:-} $1 $2"
		shift
		shift
		;;
	-h|--help)
		shift
		help
		;;
	--debug|--progress)
		analyze_passthrough="${analyze_passthrough:-} $1"
		shift
		;;
	--)
		shift
		break
		;;
	*)	break
		;;
	esac
done

if [ $# -gt 2 ]; then
	echo "$(basename $0): too many arguments" 1>&2
	usage
elif [ $# -lt 1 ]; then
	echo "$(basename $0): too few arguments" 1>&2
	usage
fi

if [ $# -eq 2 ]; then
	trace_file=$2
fi

progname=$1
if [ ${progname##/} != ${progname} ]; then
	progpath=${progname}
else
	progpath=$(pwd)/${progname}
fi

if ! test -e ${progpath}; then
	echo "$1: File not found" 1>&2
	exit 1
fi

rvpredict ${analyze_passthrough:-} ${window:---window 2000} ${parallel:-} \
    ${interrupts_target:-} \
    --json-report \
    --compact-trace ${trace_file:-./rvpredict.trace} | \
    { [ ${only_print_command:-no} = no ] && symbolize $progpath 2>&1 || cat ; }
