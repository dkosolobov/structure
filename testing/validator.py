#!/usr/bin/env python3
# Usage: run-local.py timeout program args...
# Prints: stdout_file elapsed_seconds

import subprocess
import sys
import time
import os
import resource
import urllib.request
import tempfile
import traceback
import gzip
import socket
import errno
import signal
import optparse


class Alarm(Exception):
  pass
  

def alarm_handler(signum, frame):
  raise Alarm


def open_input(path):
  if path.endswith('.gz'):
    return gzip.open(path, 'rb')
  else:
    return open(path, 'rb')


def validate(input, output, satisfiable):
  # reads solution
  solution = []
  with open(output) as f:
    answer = None
    for line in f:
      if line.startswith('c '):  # comment
        pass
      elif line.startswith('s '):
        if answer is not None:
          return 'duplicate_solution'
        answer = line[2:-1]
        if answer not in ['UNKNOWN', 'SATISFIABLE', 'UNSATISFIABLE']:
          return 'invalid_solution'
        answer = answer.lower()
        if answer != 'satisfiable':
          if satisfiable:
            return 'wrong_solution'
          return answer
      elif line.startswith('v '):
        solution.extend(line[2:].split())
  if answer is None:
    return 'missing_solution'

  # checks solution
  solution = list(map(int, solution))
  if solution[-1] != 0:
    return 'incomplete_values'
  solution = set(solution[:-1])
  for literal in solution:
    if -literal in solution:
      return 'contradictory_values'

  # reads instance
  clauses = []
  with open_input(input) as f:
    for line in f:
      line = line.decode('utf8')
      if line.startswith("c "):  # comment
        pass
      elif line.startswith("p "):  # header
        pass  # TODO: check literals
      else:
        clauses.extend(line.split())
  clauses = list(map(int, clauses))

  # checks that all clauses are satisfied
  satisfied = False
  for literal in clauses:
    if literal == 0:
      if not satisfied:
        return 'clause_not_satisfied'
      satisfied = False
    else:
      if literal in solution:
        satisfied = True

  return 'satisfiable'


def done(instance, returncode, status, elapsed):
  exit()


def main():
  parser = optparse.OptionParser(
      usage="usage: %prog [options] instance program...",
      description="SAT solver checker")
  parser.add_option('-t', dest='timeout', metavar='N', type=int, default=None,
                    help='number of seconds to allow the program to run')
  parser.add_option('-s', dest='satisfiable', action='store_true', default=False,
                    help='if instance is known to be satisfiable')
  parser.disable_interspersed_args()
  options, args = parser.parse_args()

  if len(args) < 2:
    parser.error("Not enough arguments")
  instance, program = args[0], args[1:]

  returncode = None
  status = None
  elapsed = None

  try:
    # creates termporary files
    tmpdir = tempfile.mkdtemp(prefix='structure-%s-' % os.path.basename(instance))
    stdout = os.path.join(tmpdir, 'stdout')
    stderr = os.path.join(tmpdir, 'stderr')

    # sets timeout
    if options.timeout is not None:
      signal.signal(signal.SIGALRM, alarm_handler)
      signal.alarm(options.timeout)

    start_time = time.time()
    try:
      process = subprocess.Popen(
          args=program, stdin=open('/dev/null', 'rb'),
          stdout=open(stdout, 'ab'), stderr=open(stderr, 'ab'))
      process.wait()
      signal.alarm(0)

      returncode = process.returncode
      status = validate(instance, stdout, options.satisfiable)
    except Alarm:
      process.kill();
      status = 'timedout'
  except Exception as e:
    print(e, file=sys.stderr)
    #traceback.print_exc()
    status = 'error'
  except KeyboardInterrupt as e:
    print('interrupted', file=sys.stderr)
    returncode = None
    status = 'unknown'
    system.exit(0)
  finally:
    elapsed = time.time() - start_time
    print(returncode, status, elapsed)
    exit(int(status not in ['unknown', 'satisfiable', 'unsatisfiable']))


if __name__ == '__main__':
  main()

