#!/bin/sh

(
  echo -e set terminal png
  echo -e set output \"plot.png\"
  echo -e set key top left
  echo -e set xlabel \"solved instances\"
  echo -e set ylabel \"seconds\"

  header="plot "
  for file in *.dat; do
    echo -e -n "$header" \"$file\" title \"${file%.dat}\" with linespoints
    header=", \\\\\\n     "
  done
) | gnuplot
