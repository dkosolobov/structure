#!/bin/sh

grep SATISFIABLE $1 | cut -d' ' -f4 | sort -g | awk '{print NR " " $0}'
