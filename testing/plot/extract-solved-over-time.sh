#!/bin/sh

grep satisfiable $1 | cut -d' ' -f4 | sort -g | awk '{print NR " " $0}'
