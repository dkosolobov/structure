#!/usr/bin/env python2

import sys
import random

n = int(sys.argv[1])
r = int(sys.argv[2])

clauses = []
for i in xrange(1 << n):
  result = 0
  for j in xrange(n):
    if i & (1 << j):
      result = result ^ 1

  if result == r:
    clause = []
    for j in xrange(n):
      if i & (1 << j):
        clause.append(j + 1)
      else:
        clause.append(- (j + 1))
    clause.append(0)
    clauses.append(clause)


clauses.extend(random.sample(clauses, random.randrange(len(clauses) + 1)))
random.shuffle(clauses)

print 'c', 'xor', 'gate', n, r
print 'p', 'cnf', n, len(clauses)
for clause in clauses:
  print ' '.join(str(e) for e in clause)
