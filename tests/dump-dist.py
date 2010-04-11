#!/usr/bin/env python

from __future__ import with_statement

import sqlite3


connection = sqlite3.connect('running-times.sql', isolation_level=None)
cursor = connection.cursor()

tests = ['random/160', 'random/180', 'random/200']
times = {}
keys = None

with open('dist.dat', 'w') as f:
    for test in tests:
        # my SQL sucks
        cursor.execute('''
            SELECT time, solver, mean
            FROM running_times AS a
            WHERE
                a.time=(
                    SELECT MAX(b.time)
                    FROM running_times AS b
                    WHERE b.test = a.test AND b.solver = a.solver) AND
                a.test = ? AND
                a.solver like "dist-%"''', (test,))

        times[test] = {}
        while True:
            line = cursor.fetchone()
            if line is None: break
            num_procs = int(line[1].split('-', 1)[1])
            times[test][num_procs] = line[2]

        keys_ = set(times[test].keys())
        if keys is None:
            keys = keys_
        else:
            keys &= keys_

    keys = sorted(list(keys))
    for key in keys:
        print >>f, key,
        for test in tests:
            print >>f, times[test][key],
        print >>f
