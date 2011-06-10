#!/usr/bin/env python2

import sys
import random

if len(sys.argv) < 2:
  print "Usage: %s num_variables num_xorgates\n"
  exit(1)

n = int(sys.argv[1])
m = int(sys.argv[2])

values = [random.randrange(2) for i in xrange(n)]
clauses = []

for i in xrange(m):
  lengh = random.randrange(1, 6)
  which = random.sample(range(n), l)
  sum = reduce(lambda x, y: x ^ y, values[j] for j in which)

  for j in xrange(1 << l):
    for k in xrange(l):
      result ^= ((j & (1 << k)) != 0) ^ values[which[k]]



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
