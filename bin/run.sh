#!/bin/bash


pushd `dirname $0`/.. &> /dev/null
root=$PWD
popd &> /dev/null

echo $root

prun -v -1 -np 1 java -ea                                \
    -Dibis.pool.size="$procs"                            \
    -Dibis.pool.name="structure-$RANDOM"                 \
    -Dibis.cohort.impl="mt"                              \
    -Dibis.cohort.workers="4"                            \
    -cp ".:$root/log4j.properties:$root/lib/*"           \
    ibis.structure.CohortLauncher $*

