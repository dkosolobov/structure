www/%.png: %.dat
	gnuplot $*.plt

%.dat: running-times.sql
	./dump-$*.py

.PHONY: all
all: graphs

.PHONY: clean
clean:
	rm -f *.dat www/*.png
	rm -f tmp/*

.PHONY: graphs
graphs: www/dist.png www/random-mt.png www/timeline.png


www/timeline.png: running-times.sql
	./dump-timeline.py
	gnuplot timeline.plt
	rm timeline.160.dat timeline.180.dat timeline.200.dat
