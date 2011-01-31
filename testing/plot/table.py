#!/usr/bin/env python3

import sys

def main():
  filenames = sys.argv[1:]

  results = {}
  for filename in filenames:
    with open(filename) as f:
      for line in f:
        instance, returncode, solution, elapsed = line.split()
        if solution not in ['unknown', 'satisfiable', 'unsatisfiable']:
          solution = 'unknown'

        instance_results = results.setdefault(instance, {})
        instance_results[filename] = solution, elapsed

  print('<table class="sortable">')

  print('  <thead>')
  print('    <th>instance</th>')
  for filename in filenames:
    print('    <th>%s</th>' % filename)
  print('    <th>satisfiable?</th>')
  print('  </thead>')

  print('  <tbody>')
  for instance in sorted(results.keys()):
    solutions = set()
    print('    <tr>')
    print('      <td>%s</td>' % instance)
    for filename in filenames:
      solution, elapsed = results[instance].get(filename, (None, None))
      if solution == 'unknown':
        print('      <td>-</td>')
      elif solution is None:
        print('      <td></td>')
      else:
        print('      <td>%.3f</td>' % float(elapsed))
        solutions.add(solution)

    if len(solutions) >= 2:
      print('      <td>mixed</td>')
    elif len(solutions) == 1:
      print('      <td>%s</td>' % solutions.pop())
    else:
      print('      <td></td>')

    print('    </tr>')
  print('  </tbody>')

  print('</table>')
    

if __name__ == '__main__':
  main()
