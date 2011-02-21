#!/usr/bin/env python3

import subprocess
import sys
import time
import os
import tempfile
import traceback
import gzip
import signal
import optparse


VALID_SOLUTIONS = ['SATISFIABLE', 'UNSATISFIABLE']


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
        if answer not in VALID_SOLUTIONS:
          return 'invalid_solution'
        if answer != 'SATISFIABLE':
          if satisfiable:
            return 'wrong_solution'
          return answer
      elif line.startswith('v '):
        solution.extend(line[2:].split())
  if answer is None:
    return 'missing_solution'

  # checks solution
  try:
    solution = list(map(int, solution))
  except:
    return 'invalid_values'
  if not solution or solution[-1] != 0:
    return 'missing_values'
  solution = set(solution[:-1])
  for literal in solution:
    if -literal in solution:
      return 'contradictory_values'

  # reads instance
  # NOTE: assumes that instance is correct
  clauses = []
  with open_input(input) as f:
    for line in f:
      line = line.decode('utf8')
      if line.startswith("c "):  # comment
        pass
      elif line.startswith("p "):  # header
        pass
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

  return 'SATISFIABLE'


def run(num, path, program, tmpdir, timeout, satisfiable):
  print('.', end='', file=sys.stderr)
  sys.stderr.flush()

  # creates temporary files
  stdout = os.path.join(tmpdir, 'stdout-%d' % num)
  stderr = os.path.join(tmpdir, 'stderr-%d' % num)

  # sets timeout
  if timeout is not None:
    signal.signal(signal.SIGALRM, alarm_handler)
    signal.alarm(timeout)

  start_time = time.time()
  returncode = None
  try:
    process = subprocess.Popen(
        args=program, stdin=open('/dev/null', 'rb'),
        stdout=open(stdout, 'ab'), stderr=open(stderr, 'ab'))
    process.wait()
    signal.alarm(0)
    elapsed = time.time() - start_time
    status = validate(path, stdout, satisfiable)
    if status not in VALID_SOLUTIONS: elapsed = None
  except Alarm:
    process.kill();
    elapsed = None
    status = 'timedout'

  returncode = process.returncode
  return [returncode, status, elapsed]


def main():
  parser = optparse.OptionParser(
      usage="usage: %prog [options] instance program...",
      description="SAT solver checker")
  parser.add_option('-t', dest='timeout', metavar='N', type=int, default=None,
                    help='number of seconds the program is allowed to run')
  parser.add_option('-r', dest='repeat', metavar='N', type=int, default=3,
                    help='number of times to repeat measurements')
  parser.add_option('-p', dest='path', metavar='PATH', type=str, default='%s',
                    help='location of instance. \%s is replaced by instance name')
  parser.add_option('-s', dest='satisfiable', action='store_true', default=False,
                    help='if instance is known to be satisfiable')
  parser.disable_interspersed_args()
  options, args = parser.parse_args()

  if len(args) < 2:
    parser.error("Not enough arguments")
  instance, program = args[0], args[1:]
  path = options.path % instance

  elapsed = None
  tmpdir = tempfile.mkdtemp(prefix='structure-%s-' % os.path.basename(instance))
  try:
    for r in range(options.repeat):
      run_returncode, run_status, run_elapsed = run(
          r, path, program, tmpdir, options.timeout, options.satisfiable)
      if run_status not in VALID_SOLUTIONS:
        returncode, status, elapsed = run_returncode, run_status, None
        break

      if r == 0:
        # Set the expected results
        returncode, status = run_returncode, run_status
        elapsed = [run_elapsed]
      else:
        if returncode != run_returncode or status != run_status:
          # Results don't match
          returncode, status, elapsed = None, 'inconsistent_results', None
          break
        else:
          # Extra set
          elapsed.append(run_elapsed)
  except Exception as e:
    # Unexpected error in script itself
    print(e, file=sys.stderr)
    traceback.print_exc()
    status = 'error'
  except KeyboardInterrupt as e:
    print('interrupted', file=sys.stderr)
    returncode, status, elapsed = None, 'interrupted', None
  finally:
    if status in VALID_SOLUTIONS:
      avg = sum(elapsed) / len(elapsed)
      std = (sum((e - avg)**2 for e in elapsed) / len(elapsed)) ** 0.5
      ci = 1.96 * std / (len(elapsed) ** 0.5)
      print(instance, returncode, status, avg, ci)
    else:
      print(instance, None, status, None, None)

    sys.stdout.flush()
    exit(int(status not in ['interrupted'] + VALID_SOLUTIONS))


if __name__ == '__main__':
  main()

