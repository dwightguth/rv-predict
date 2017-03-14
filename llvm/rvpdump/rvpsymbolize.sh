#!/bin/sh

set -e

tmpdir=$(mktemp -d)
regex='at \(0x[0-9a-f]\+\) dummy.c:999'
tee $tmpdir/original | \
grep "$regex" | sed "s/.*$regex.*/\1/g" | sort -u | \
    llvm-symbolizer -obj $1 -inlining -print-address | \
    awk 'BEGIN {RS = "" ; FS = "\n"} /^0x/,/^$/ { print $1 " " gensub(" ", "\\\\ ", "g", $2) " " $3; }' | \
while read addr symbol path; do
	echo $addr ${symbol}@$(basename $path)
	
done | \
    sed 's,^0x\([0-9a-f]\+\) \([^@]\+\)@\(.*\)$,s|at 0x0\\+\1 dummy.c:999|at \2 \3|g,' > $tmpdir/sed_script

sed -f $tmpdir/sed_script < $tmpdir/original

rm -rf $tmpdir

exit 0