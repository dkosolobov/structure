#!/usr/bin/env python3
# Usage: run-local.py timeout program args...
# Prints: stdout_file elapsed_seconds

import subprocess
import sys
import time
import tempfile
import os
import resource

timeout = int(sys.argv[1])
program = sys.argv[2:]

def set_cpulimit():
  resource.setrlimit(resource.RLIMIT_CPU, (timeout, timeout))

tmpdir = tempfile.mkdtemp(dir='./tmp')
stdout = os.path.join(tmpdir, 'stdout')
stderr = os.path.join(tmpdir, 'stderr')

start_time = time.time()
process = subprocess.Popen(
    args=["/usr/bin/timeout", str(2 * timeout)] + program,
    preexec_fn=set_cpulimit,
    stdout=open(stdout, 'a'),
    stderr=open(stderr, 'a'))
process.wait()
end_time = time.time()

print(stdout, end_time - start_time)
exit(process.returncode)
