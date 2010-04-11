set terminal png
set output "www/timeline.png"

set title "Timeline"
set xlabel "Moment in time"
set noxtics
set ylabel "seconds"
set grid ytics mytics

plot "timeline.160.dat" using 1:2 with linespoints title  "random/160", \
     "timeline.180.dat" using 1:2 with linespoints title  "random/180", \
     "timeline.200.dat" using 1:2 with linespoints title  "random/200"
