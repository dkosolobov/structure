set terminal png
set output "dist.png"

set title "Distributed Cohort"
set key autotitle columnhead
set xlabel "processors"
set ylabel "seconds"

plot "dist.dat" using 1:2 with linespoints, \
     "dist.dat" using 1:3 with linespoints, \
     "dist.dat" using 1:4 with linespoints
