set terminal png
set output "random-mt.png"

set key top left
set title "Random instances on mt"
set xlabel "variables"
set ylabel "seconds"

plot "random-mt.dat" using 2:xticlabels(1) title "1 thread" with linespoints,   \
     "random-mt.dat" using 3:xticlabels(1) title "2 threads" with linespoints,  \
     "random-mt.dat" using 4:xticlabels(1) title "4 threads" with linespoints,  \
     "random-mt.dat" using 5:xticlabels(1) title "sat4j" with linespoints


