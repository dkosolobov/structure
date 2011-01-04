#!/usr/bin/env python3
# Usage: run-local.py input answer timeout
# Prints: message elapsed

import subprocess
import sys
import time
import tempfile
import os

input = sys.argv[1]
correct = sys.argv[2]
timeout = int(sys.argv[3])

if not os.path.isfile(input):
  print('missing', 0.0)
  exit()

tmpdir = tempfile.mkdtemp(dir='./tmp')
output = os.path.join(tmpdir, 'output')
stdout = os.path.join(tmpdir, 'stdout')
stderr = os.path.join(tmpdir, 'stderr')

start_time = time.time()
process = subprocess.Popen(
    args=['/usr/bin/timeout', str(timeout), '../../cryptominisat', input, output],
    stdout=open(stdout, 'a'),
    stderr=open(stderr, 'a'))
process.wait()
end_time = time.time()

answer = {
    0 : 'unknown',
    10 : 'satisfiable',
    20 : 'unsatisfiable',
    124 : 'timeout',
  }.get(process.returncode, 'error')
if answer == 'satisfiable' and correct == 'unsatisfiable':
  answer = 'invalid'
if answer == 'unsatisfiable' and correct == 'satisfiable':
  answer = 'invalid'

print(answer, end_time - start_time)
