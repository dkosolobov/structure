#!/usr/bin/env python
#
# A script to generate random sat formulas inspired from the paper
# Achlioptas, Jia and Moore: Hiding Satisfying Assignments: Two are Better than One, 
#
# TODO: sometimes the maximum number of clauses to be generated is lower than
# the number of clauses requested and thus the program never finishes

import sys
import optparse
import random

parser = optparse.OptionParser('usage: %prog [options] variables')
parser.add_option('-r', '--ratio', dest='ratio', type='float', default=5.,
                  help='ratio between the number of clauses and the number of variables', metavar='R')
parser.add_option('-k', '--literals', dest='literals', type='int', default=5,
                  help='literals per clause', metavar='K')
parser.add_option('-s', '--seed', dest='seed', type='int', default=None,
                  help='seed for the random number generator', metavar='SEED')

try:
   options, args = parser.parse_args()
   variables = int(args[0])
   clauses = int(variables * options.ratio)
   literals = options.literals
   random.seed(options.seed)
except ValueError:
   parser.print_usage()
   exit(1)


sequence = range(variables)

while True:
   # generates a random assignment
   assignment = [random.choice([-1, +1]) for v in xrange(variables)]
   formula = set()

   while len(formula) < clauses:
      found = False
      while not found:
         # selects some random variables
         clause = random.sample(sequence, literals)
         # and randomly creates a clause
         good = [False, False]
         for l in xrange(literals):
            flip = random.choice([-1, +1])
            good[0] = good[0] or (+ assignment[clause[l]] * flip) > 0
            good[1] = good[1] or (- assignment[clause[l]] * flip) > 0
            clause[l] = (clause[l] + 1) * flip

         # clause must satisfy both assignment and complented assignment
         found = good[0] and (literals == 2 or good[1])

      clause.sort()
      clause = tuple(clause)
      if clause not in formula:
         formula.add(clause)
    
   _ = set()
   for clause in formula:
      _.update(clause)
   if len(_) == 2 * variables:
      break

print 'c', variables, 'variables,', clauses, 'clauses,', literals, 'literals per clause'
print 'c',
for v in xrange(variables):
   print (v + 1) * assignment[v],
print

print 'p', 'cnf', variables, clauses
for clause in formula:
   for literal in clause:
      print literal,
   print 0
