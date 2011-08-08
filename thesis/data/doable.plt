set terminal postscript enhanced color

root = "`echo $ROOT`"
index = `echo $INDEX`

set size 0.75,0.50
set ylabel "Time (s)"
set grid x y
set style line 1 linewidth 3

if (index == 0) set key top left
if (index != 0) set key bottom right
if (index == 2) set xlabel "Number of instances"

set output root."/large.eps"
set xrange [0:250]
set yrange [0:3000]
set title "45 minutes timeout (".root.")"
plot root."/large-8.dat" using 0:1 title "8 HT cores" with lines lw 3 lt 1 lc 1, \
     root."/large-16.dat" using 0:1 title "16 HT cores" with lines lw 3 lt 1 lc 2


set output root."/flp.eps"
set xrange [0:200]
set yrange [0:900]
set title "Failed Literal Probing (".root.")"
plot root."/flp-0.dat" using 0:1 title "0 probes" with lines lw 3 lt 1 lc 1, \
     root."/flp-1.dat" using 0:1 title "16 probes" with lines lw 3 lt 1 lc 2, \
     root."/flp-2.dat" using 0:1 title "32 probes" with lines lw 3 lt 1 lc 3, \
     root."/flp-4.dat" using 0:1 title "64 probes" with lines lw 3 lt 1 lc 4, \
     root."/flp-8.dat" using 0:1 title "128 probes" with lines lw 3 lt 1 lc 5, \
     root."/flp-16.dat" using 0:1 title "256 probes" with lines lw 3 lt 1 lc 6

set output root."/disable.eps"
set title "Simplification procedures (".root.")"
plot root."/default.dat" using 0:1 title "default" with lines lw 3 lt 1 lc 1, \
     root."/disable-nobce.dat" using 0:1 title "nobce" with lines lw 3 lt 1 lc 2, \
     root."/disable-nolearn.dat" using 0:1 title "nolearn" with lines lw 3 lt 1 lc 3, \
     root."/disable-nosplit.dat" using 0:1 title "nosplit" with lines lw 3 lt 1 lc 4, \
     root."/disable-nove.dat" using 0:1 title "nove" with lines lw 3 lt 1 lc 5, \
     root."/disable-noxor.dat" using 0:1 title "noxor" with lines lw 3 lt 1 lc 6

set output root."/para-1X.eps"
set title "1 Node with Increasing Number of Cores (".root.")"
plot root."/para-11.dat" using 0:1 title "1 core" with lines lw 3 lt 1 lc 1, \
     root."/para-12.dat" using 0:1 title "2 cores" with lines lw 3 lt 1 lc 2, \
     root."/para-14.dat" using 0:1 title "4 cores" with lines lw 3 lt 1 lc 3, \
     root."/para-18.dat" using 0:1 title "8 cores" with lines lw 3 lt 1 lc 4
