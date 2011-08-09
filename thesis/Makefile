.PHONY: all
all: thesis

.PHONY: clean
clean:
	rm -f thesis.pdf

.PHONY: thesis
thesis:
	latex thesis.tex
	dvipdf thesis.dvi

.PHONY: bibtex
bibtex:
	latex thesis.tex
	bibtex thesis
	latex thesis.tex
	latex thesis.tex
