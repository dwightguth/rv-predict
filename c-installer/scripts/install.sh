#!/bin/sh

set -e

SCRIPT=$(readlink -f "$0")
SCRIPTPATH=$(dirname "$SCRIPT")

sh -c "RV_PREDICT_LICENSE_ACCEPTED='yes' dpkg -i \"$SCRIPTPATH/../rv-predict-c-$1.deb\"" || apt-get install -f -y
sh -c "RV_PREDICT_LICENSE_ACCEPTED='yes' dpkg -i \"$SCRIPTPATH/../rv-predict-c-$1.deb\""
