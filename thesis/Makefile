 
.PHONY: all
all: thesis.pdf

.PHONY: clean
clean:
	rm -f thesis.pdf

thesis.pdf: thesis.tex          \
						abstract.tex        \
						introduction.tex    \
						architecture.tex    \
						reasoning.tex       \
						conclusions.tex
	pdflatex $^
