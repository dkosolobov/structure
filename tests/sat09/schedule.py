#!/usr/bin/env python
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
import threading
import time


TRACKS = ['random', 'application', 'crafted']
LEVELS = ['easy', 'medium', 'hard']
SOLUTIONS = ['UNKNOWN', 'SAT', 'UNSAT']

tracks = None
levels = None
timeout = None
args = None
include_unknown = None

jobs, jobs_lock = [], threading.Lock()
start = None
stop = threading.Semaphore(0)
execution_times = {}

Job = collections.namedtuple('Job', 'path track level satisfiable process')

def eval_queue():
  start_time = time.time()
  num_solved = 0

  while True:
    delta = datetime.timedelta(seconds=time.time() - start_time)
    print('Checking running jobs at', str(delta),
          '. Solved', num_solved, file=sys.stderr)
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
      start.release()
      stop.release()

      answer, elapsed = job.process.stdout.readline().split()
      answer, elapsed = answer.decode('utf-8'), float(elapsed)
      num_solved += 1

      print(job.path, job.track, job.level, answer, elapsed)
      sys.stdout.flush()

    time.sleep(0.999)

def eval_file(path, track, level, satisfiable):
  start.acquire()
  program = [e.format(input=path, correct=str(satisfiable)) for e in args]
  process = subprocess.Popen(args=program, stdout=subprocess.PIPE)
  with jobs_lock:
    jobs.append(Job(path, track, level, satisfiable, process))


def main():
  global args
  parser = optparse.OptionParser()
  parser.add_option("--track", dest="track",
                    help="%s" % TRACKS, default=TRACKS)
  parser.add_option("--level", dest="level",
                    help="%s" % LEVELS, default=LEVELS)
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

  # reads maxjobs
  global start
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
