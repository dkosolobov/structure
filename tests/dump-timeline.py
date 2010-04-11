#!/usr/bin/env python

from __future__ import with_statement

import sqlite3


connection = sqlite3.connect('running-times.sql', isolation_level=None)
cursor = connection.cursor()

tests = ['random/160', 'random/180', 'random/200']
times = {}
keys = None

for test in tests:
    # my SQL sucks
    cursor.execute('''
        SELECT time, mean
        FROM running_times AS a
        WHERE a.test = ? AND a.solver = "mt-4"''', (test,))

    times[test] = []
    while True:
        line = cursor.fetchone()
        if line is None: break
        times[test].append(line)


for test in times:
    num_vars = int(test.split('/', 1)[1])
    with open('timeline.%d.dat' % num_vars, 'w') as f:
        for time in times[test]:
            print >>f, time[0], time[1]
