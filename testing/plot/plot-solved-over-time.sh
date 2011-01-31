#!/bin/sh

(
  echo -e set terminal png size 800 600
  #echo -e set output \"plot.png\"
  echo -e set key top left
  echo -e set xlabel \"solved instances\"
  echo -e set ylabel \"seconds\"
  echo -e set mxtics
  echo -e set mytics
  echo -e set grid

  header="plot "
  for file in $*; do
    data_file=`mktemp`
    `dirname $0`/extract-solved-over-time.sh $file > $data_file

    echo -e -n "$header" \"$data_file\" title \"${file%.dat}\" with linespoints
    header=", \\\\\\n     "
  done
) | gnuplot
