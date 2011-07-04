set terminal postscript enhanced color

set xlabel "Total time (150s timeout)"
set ylabel "Minimum instance size"

f(x) = a*x + b

set output "hur.eps"
plot "< sort -n -k1 instancessizes" using 1:4 notitle with lines smooth bezier

set output "hte.eps"
fit f(x) "< sort -n -k2 instancessizes" using 2:4 via a, b
plot "< sort -n -k2 instancessizes" using 2:4 notitle with lines smooth bezier, f(x) notitle

set output "sss.eps"
fit f(x) "< sort -n -k3 instancessizes" using 3:4 via a, b
plot "< sort -n -k3 instancessizes" using 3:4 notitle with lines smooth bezier, f(x) notitle
 
 
