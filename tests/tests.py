#!/usr/bin/env python

import subprocess
import os
import sys
import os.path


structure_location = os.getenv('STRUCTURE_LOCATION')
tmpdir = os.path.join(structure_location, 'tests/tmp')
groups = ['random']


def sat4j_Solver():
    return {
        'STRUCTURE_LOCATION': structure_location,
        'STRUCTURE_TMPDIR': tmpdir,
        'SOLVER': 'run-sat4j',
        'SOLVER_PROCS': '1',
    }


def mt_Solver(threads):
    return {
        'STRUCTURE_LOCATION': structure_location,
        'STRUCTURE_TMPDIR': tmpdir,
        'SOLVER': 'run-structure',
        'SOLVER_PROCS': '1',
        'SOLVER_IMPL': 'mt',
        'SOLVER_THREADS': str(threads),
    }


def dist_Solver(procs, threads):
    return {
        'STRUCTURE_LOCATION': structure_location,
        'STRUCTURE_TMPDIR': tmpdir,
        'SOLVER': 'run-structure',
        'SOLVER_PROCS': str(procs),
        'SOLVER_IMPL': 'dist',
        'SOLVER_THREADS': str(threads),
    }


def solverFactory(name, procs):
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



def executeGroup(group, solver, procs):
    os.chdir(group)

    environ = os.environ.copy()
    environ.update(solverFactory(solver, procs))

    p = subprocess.Popen(['run'],
        stdout=subprocess.PIPE,
        env=environ);
    p.wait()

    timings = {}
    for line in p.stdout:
        line = line.split()
        timings[line[0]] = line[1]

    os.chdir('..');
    return timings


def updateTimings(timings, group, procs, tests):
    for key, value in tests.iteritems():
        timings.setdefault(group + '/' + key, {})[procs] = value


def write(solver, procs, tests):
    f = open(solver + '.dat', 'w')

    # print header
    print >>f, 'processors',
    for key in tests:
        print >>f, key,
    print >>f

    # prints contents
    for i, p in enumerate(procs):
        print >>f, p,
        for times in tests.itervalues():
            print >>f, times[i],
        print >>f

    f.close()


def main():
    solvers = {
        'sat4j': [1],
        'mt'   : [1, 2, 4],
        'dist' : [1, 2, 4, 8, 16, 32],
    }

    timings = {}  # timings[solver][test][procs] = seconds
    for solver, procs in solvers.iteritems():
        timings[solver] = {}

        for group in groups:
            for p in procs:
                print >>sys.stderr, 'solving %s using %s on %d processor(s)' % (
                        group, solver, p)
                tests = executeGroup(group, solver, p)
                print >>sys.stderr, 'timings ', tests
                updateTimings(timings[solver], group, p, tests)

    # random-mt
    f = open('www/random-mt.dat', 'w')
    for test in sorted(timings['mt'].keys()):
        if not test.startswith('random/'):
            continue

        print >>f, int(test[-4:]),
        for proc in solvers['mt']:
            print >>f, timings['mt'][test][proc],
        print >>f, timings['sat4j'][test][1],
        print >>f
    f.close()

    # dist
    f = open('www/dist.dat', 'w')
    tests = ['random/random-0120', 'random/random-0140', 'random/random-0160']
    print >>f, 'procs',
    for test in tests:
        print >>f, test,
    print >>f
    for proc in solvers['dist']:
        print >>f, proc,
        for test in tests:
            print >>f, timings['dist'][test][proc],
        print >>f
    f.close()


if __name__ == '__main__':
    main()
