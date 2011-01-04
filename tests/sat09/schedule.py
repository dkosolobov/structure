#!/usr/bin/env python
#
# Reference:
#  http://www.satcompetition.org/2009/format-solvers2009.html
#
# Usage: eval.py tracks timeout program args...
#
# tracks - 'random', 'application', 'crafted'
# levels - 'easy', 'medium', 'hard'
# timeout - maximum seconds to run (0 for no timeout)
# program - launched with program args... path

import collections
import datetime
import optparse
import os
import subprocess
import sys
import tempfile
import threading
import time


TRACKS = ['random', 'application', 'crafted']
LEVELS = ['easy', 'medium', 'hard']
SOLUTIONS = ['UNKNOWN', 'SAT', 'UNSAT']
POLL_INTERVAL = 0.25

tracks = None
levels = None
timeout = None
args = None

jobs, jobs_lock = [], threading.Lock()
start = None
stop = threading.Semaphore(0)
execution_times = {}

Job = collections.namedtuple('Job', 'input output track level satisfiable start_time process')


def check(job):
  # reads solution
  solution = []
  with open(job.output) as output:
    answer = None
    for line in output:
      if line.startswith("c "):  # comment
        pass
      elif line.startswith("s "):
        answer = line[2:].strip()
        if answer not in ['UNKNOWN', 'SATISFIABLE', 'UNSATISFIABLE']:
          return 'invalid_solution'
        answer = answer.lower()
        if answer == 'unknown':
          return answer
        if job.satisfiable != 'unknown':
          if answer != job.satisfiable:
            return 'incorrect_solution'
        if answer == 'unsatisfiable':
          return answer
      elif line.startswith("v "):
        solution.extend(line[2:].split())

  if answer is None:
    return 'missing_solution'
  assert answer == 'satisfiable'

  solution = list(map(int, solution))
  if solution[-1] != 0:
    return 'incomplete_values'

  solution = set(solution[:-1])
  for literal in solution:
    if -literal in solution:
      return 'contradictory_values'

  # reads input
  clauses = []
  with open(job.input) as input:
    for line in input:
      if line.startswith("c "):  # comment
        pass
      elif line.startswith("p "):  # header
        pass  # TODO: 
      else:
        clauses.extend(line.split())
  clauses = list(map(int, clauses))

  # checks clauses
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

def eval_queue():
  eval_start_time = time.time()
  num_solved = 0

  while True:
    current_time = time.time()
    finished = []
    running = []

    with jobs_lock:
      global jobs
      for job in jobs:
        if job.process.poll() is None:
          running.append(job)
        else:
          finished.append(job)
      jobs = running

    for job in finished:
      # elapsed has a small (acceptable) error
      elapsed = current_time - job.start_time

      if job.process.returncode == 124:
        status = 'timeout'
      elif job.process.returncode == 127:
        status = 'missing_executable'
      else:
        status = check(job)

      print(job.input, job.track, job.level, status, elapsed)
      sys.stdout.flush()

      start.release()
      stop.release()

    if finished: 
      num_solved += len(finished)
      delta = datetime.timedelta(seconds=current_time - eval_start_time)
      print('Solved', num_solved, 'after', str(delta), file=sys.stderr)

    time.sleep(POLL_INTERVAL - 0.001)


def eval_file(path, track, level, satisfiable):
  start.acquire()

  tmpdir = tempfile.mkdtemp(prefix=os.path.basename(path) + "_", dir='./tmp')
  stdout = os.path.join(tmpdir, 'stdout')
  stderr = os.path.join(tmpdir, 'stderr')
  program = [e.format(input=path) for e in args]

  start_time = time.time()
  process = subprocess.Popen(
      args=['/usr/bin/timeout', str(timeout)] + program,
      stdout=open(stdout, 'a'), stderr=open(stderr, 'a'))
  process.wait()

  with jobs_lock:
    jobs.append(Job(input=path, output=stdout, track=track,
                    level=level, satisfiable=satisfiable,
                    start_time=start_time, process=process))


def main():
  global args
  parser = optparse.OptionParser()
  parser.add_option("--track", dest="track",
                    help="%s" % TRACKS, default=TRACKS)
  parser.add_option("--level", dest="level",
                    help="%s" % LEVELS, default=LEVELS)
  parser.add_option("--timeout", dest="timeout",
                    help="maximum number of seconds to run", type=int, default=0)
  parser.add_option("--include_unknown", dest="include_unknown",
                    help="include instances with unknown solution", action="store_true", default=False)
  parser.add_option("--maxjobs", dest="maxjobs",
                    help="maximum number of jobs", type=int, default=2)
  options, args = parser.parse_args()

  # parse tracks
  global tracks
  tracks = options.track
  if isinstance(tracks, str):
    tracks = tracks.lower().split(',')
    assert set(tracks) <= set(TRACKS)

  # parse levels
  global levels
  levels = options.level
  if isinstance(levels, str):
    levels = levels.lower().split(',')
    assert set(levels) <= set(LEVELS)

  # reads timeout, maxjobs
  global timeout, start
  timeout = options.timeout
  start = threading.Semaphore(options.maxjobs)
  include_unknown = options.include_unknown

  thread = threading.Thread(target=eval_queue)
  thread.daemon = True
  thread.start()

  num_jobs = 0
  for track in tracks:
    with open('selection-%s.txt' % track) as handler:
      print('Running track', track, file=sys.stderr);
      for line in handler:
        line = line.split()
        level, path, satisfiable = line[0].lower(), line[1], line[-1]
        assert level in LEVELS and satisfiable in SOLUTIONS
        satisfiable = ('unknown', 'satisfiable', 'unsatisfiable')[SOLUTIONS.index(satisfiable)]

        if level in levels and (satisfiable != 'unknown' or include_unknown):
          eval_file(path, track, level, satisfiable)
          num_jobs += 1
      print(file=sys.stderr)

  # awaits all jobs to finish
  for i in range(num_jobs):
    stop.acquire()

if __name__ == '__main__':
  main()
