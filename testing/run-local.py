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


class Alarm(Exception):
  pass
  

def alarm_handler(signum, frame):
  raise Alarm


def open_input(path):
  if path.endswith('.gz'):
    return gzip.open(path, 'rb')
  else:
    return open(path, 'rb')


def validate(input, output):
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
  timeout = int(sys.argv[1])
  instance = sys.argv[2]
  url = sys.argv[3]

  returncode = None
  status = None
  elapsed = None

  try:
    while True:
      # loops on connection reset
      try:
        filename, headers = urllib.request.urlretrieve(url)
        break
      except IOError as e:
        if e.errno == 'socket error':
          if e.args[1] in [errno.ECONNRESET]:
            raise
      except Exception as e:
        raise

    program = [e.format(instance=filename) for e in sys.argv[4:]]

    tmpdir = tempfile.mkdtemp(prefix='structure-%s-' % os.path.basename(instance))
    stdout = os.path.join(tmpdir, 'stdout')
    stderr = os.path.join(tmpdir, 'stderr')

    signal.signal(signal.SIGALRM, alarm_handler)
    signal.alarm(timeout)

    start_time = time.time()
    try:
      process = subprocess.Popen(
          args=program, stdin=open('/dev/null', 'rb'),
          stdout=open(stdout, 'ab'), stderr=open(stderr, 'ab'))
      process.wait()
      signal.alarm(0)

      returncode = process.returncode
      status = validate(filename, stdout)
    except Alarm:
      status = 'timedout'
      
    end_time = time.time()
    elapsed = end_time - start_time
  except Exception as e:
    print(e, file=sys.stderr)
    status = 'error'
  finally:
    print(instance, returncode, status, elapsed)


if __name__ == '__main__':
  main()

