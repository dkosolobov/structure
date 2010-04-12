set terminal png
set output "www/dist.png"

set title "Distributed Cohort"
set xlabel "processors"
set xtics (1,2,4,8,16,32)
set ylabel "seconds"
set grid ytics mytics
set logscale y

plot "dist.dat" using 1:2 with linespoints title "random/220", \
     "dist.dat" using 1:3 with linespoints title "random/240", \
     "dist.dat" using 1:4 with linespoints title "random/260"
