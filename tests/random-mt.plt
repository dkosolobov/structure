set terminal png
set output "www/random-mt.png"

set key top left
set title "Random instances on multithreaded Cohort"
set xlabel "variables"
set ylabel "seconds"

#set xrange [20:200]
set xtics 20
set grid ytics mytics
set logscale y

plot "random-mt.dat" using 1:2 title "1 thread" with linespoints,   \
     "random-mt.dat" using 1:3 title "2 threads" with linespoints,  \
     "random-mt.dat" using 1:4 title "4 threads" with linespoints,  \
     "random-mt.dat" using 1:5 title "sat4j" with linespoints


