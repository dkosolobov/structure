#!/bin/sh

grep satisfiable $* | cut -d' ' -f5 | sort -g | awk '{print NR " " $0}'