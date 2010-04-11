#!/usr/bin/env python

from __future__ import with_statement

import subprocess
import sqlite3
import os
import math
import sys
import os.path

from time import sleep, time


STRUCTURE_LOCATION = os.getenv('STRUCTURE_LOCATION')
TMPDIR = os.path.join(STRUCTURE_LOCATION, 'tests/tmp')
GROUPS = ['random']
MAX_PROCESSES = 16

cursor = None


class TestResults(object):
    def __init__(self, name, solver, num_files):
        self.name = name
        self.solver = solver
        self.num_files = num_files
        self.times = []

    def finished(self, elapsed):
        self.times.append(elapsed)

        if len(self.times) == self.num_files:
            self.mean = sum(self.times) / self.num_files
            self.stddev = math.sqrt(
                    sum((x - self.mean) ** 2 for x in self.times) / self.num_files)
            self.min = min(self.times)
            self.max = min(self.times)

            print 'finished %s using %s: mean=%.3f, stddev=%.3f, min=%.3f, max=%.3f' % (
                    self.name, self.solver, self.mean, self.stddev, self.min, self.max)

            cursor.execute('insert into running_times values (?, ?, ?, ?, ?, ?, ?)', (
                    time(), self.name, self.solver, self.mean, self.stddev, self.min, self.max))


def sat4j_Solver():
    return {
        'STRUCTURE_LOCATION': STRUCTURE_LOCATION,
        'STRUCTURE_TMPDIR': TMPDIR,
        'SOLVER': 'run-sat4j',
        'SOLVER_PROCS': '1',
    }


def mt_Solver(threads):
    return {
        'STRUCTURE_LOCATION': STRUCTURE_LOCATION,
        'STRUCTURE_TMPDIR': TMPDIR,
        'SOLVER': 'run-structure',
        'SOLVER_PROCS': '1',
        'SOLVER_IMPL': 'mt',
        'SOLVER_THREADS': str(threads),
    }


def dist_Solver(procs, threads):
    return {
        'STRUCTURE_LOCATION': STRUCTURE_LOCATION,
        'STRUCTURE_TMPDIR': TMPDIR,
        'SOLVER': 'run-structure',
        'SOLVER_PROCS': str(procs),
        'SOLVER_IMPL': 'dist',
        'SOLVER_THREADS': str(threads),
    }


def solverFactoryHelper(name, procs):
    if name == 'sat4j':
        assert procs == 1
        return sat4j_Solver()

    if name == 'mt':
        assert 1 <= procs <= 4
        return mt_Solver(procs)

    if name == 'dist':
        if 1 <= procs <= 4:
            return dist_Solver(1, procs)

        assert procs % 4 == 0
        return dist_Solver(procs / 4, 4)

    assert False, 'Unknown solver %s' % name


def solverFactory(name, procs):
    return '%s-%d' % (name, procs), solverFactoryHelper(name, procs)


def loadGroup(group):
    tests = {}

    with open(os.path.join(group, 'list')) as f:
        for fullline in f:
            if fullline[0] == '#':
                # ignores comments
                continue
                
            test, files = fullline.split('=', 1)
            test = test.strip()
            files = files.split()
            tests[test] = [os.path.join(group, file) for file in files]

    return tests


def executeTests(tests, solvers):
    results = {}
    todo = []     # [(file, test, solver), ...]

    for solver in solvers:
        for test, files in tests.iteritems():
            results[(test, solver[0])] = TestResults(test, solver[0], len(files))
            for file in files:
                todo.append((file, test, solver))

    running = []  # [((file, test, solver), process), ...]
    while todo or running:
        running_ = []
        for run in running:
            file, test, solver = run[0]
            process = run[1]

            process.poll()

            if process.returncode is None:
                # process didn't finish yet
                running_.append(run)
                continue


            elif process.returncode:
                # process finished with error
                print 'solving instance %s (test %s) using %s failed. restarting' % (
                        file, test, solver[0])
                todo.append(run[0])
            else:
                # process finish
                time = float(run[1].stdout.readline())
                print 'finished instance %s (test %s) using %s in %.3fs' % (
                        file, test, solver[0], time)
                results[(test, solver[0])].finished(time)
        running = running_

        
        while todo and len(running) < MAX_PROCESSES:
            # starts new processes
            file, test, solver = todo.pop()
            environ = os.environ.copy()
            environ.update(solver[1])

            process = subprocess.Popen(
                args=('./run-test', file),
                stdout=subprocess.PIPE,
                env=environ)

            print ' solving instance %s (test %s) using %s' % (
                    file, test, solver[0])
            running.append(((file, test, solver), process))

        
        # print 'sleeping'
        if todo or running:
            sleep(0.5)


            
def main():
    global cursor
    connection = sqlite3.connect('running-times.sql', isolation_level=None)
    cursor = connection.cursor()

    tests = {}
    for group in GROUPS:
        group_tests = loadGroup(group)
        for test, files in group_tests.iteritems():
            tests[group + '/' + test] = files

    solvers = [solverFactory('mt', 4)]
    executeTests(tests, solvers)


if __name__ == '__main__':
    main()
