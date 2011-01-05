#!/bin/sh

(
  echo -e set terminal png
  echo -e set output \"plot.png\"
  echo -e set key top left
  echo -e set xlabel \"solved instances\"
  echo -e set ylabel \"seconds\"

  header="plot "
  for file in $*; do
    data_file=`mktemp`
    ./extract-solved-over-time.sh $file > $data_file

    echo -e -n "$header" \"$data_file\" title \"${file%.dat}\" with linespoints
    header=", \\\\\\n     "
  done
) | gnuplot
