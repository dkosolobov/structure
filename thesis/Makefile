.PHONY: all
all: thesis.pdf

.PHONY: clean
clean:
	rm -f thesis.pdf

.PHONY: thesis.pdf
thesis.pdf: thesis.tex          \
						abstract.tex        \
						introduction.tex    \
						literature.tex      \
						sat.tex             \
						architecture.tex    \
						results.tex         \
						conclusions.tex     \
						easy-instances.tex  \
						algorithms.tex
	latex thesis.tex; dvipdf thesis.dvi
