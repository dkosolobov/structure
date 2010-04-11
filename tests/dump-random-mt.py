#!/usr/bin/env python

from __future__ import with_statement

import sqlite3


connection = sqlite3.connect('running-times.sql', isolation_level=None)
cursor = connection.cursor()

solvers = ['mt-1', 'mt-2', 'mt-4', 'sat4j-1']
times = {}
keys = None

with open('random-mt.dat', 'w') as f:
    for solver in solvers:
        # my SQL sucks
        cursor.execute('''
            SELECT time, test, mean
            FROM running_times AS a
            WHERE
                a.time=(
                    SELECT MAX(b.time)
                    FROM running_times AS b
                    WHERE b.test = a.test AND b.solver = a.solver) AND
                a.solver = ?''', (solver,))

        times[solver] = {}
        while True:
            line = cursor.fetchone()
            if line is None: break

            num_vars = int(line[1].split('/', 1)[1])
            times[solver][num_vars] = line[2]

        keys_ = set(times[solver].keys())
        if keys is None:
            keys = keys_
        else:
            keys &= keys_

    keys = sorted(list(keys))
    for key in keys:
        print >>f, key,
        for solver in solvers:
            print >>f, times[solver][key],
        print >>f


