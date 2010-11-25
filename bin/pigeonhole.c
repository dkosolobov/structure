// ****************************************************************************
// A small C program to create DIMACS CNF files that encode the pigeonhole
// problem (i.e. placing n+1 pigeons into n holes).
//
// The SAT encoding of this problem is very straightforward.  For each pigeon i
// and each hole j we have a variable x_{n*(i-1)+j} which means that pigeon i
// is placed in hole j.  Then we have n+1 clauses which say that a pigeon has
// to be placed in some hole.  Then for each hole we have a set of clauses
// ensuring that only one single pigeon is placed into that hole.
//
// This encoding leads to a total of (n+1) * n propositional variables and
// (n+1) + n * (n * (n+1) / 2) clauses.
//
// The resulting SAT problem is unsatisfiable.
//
// Version 1.0, 2007-03-15
// (c) 2007 Tjark Weber
// ****************************************************************************

#include <stdlib.h>
#include <stdio.h>

void usage() {
  printf("Usage:\n");
  printf("\n");
  printf("  pigeonhole n\n");
  printf("\n");
  printf("where n>0 is the number of holes (the number of pigeons is n+1).\n");
  exit(1);
}

int main(int argc, char** argv) {

  int n;  // n+1 pigeons in n holes

  int i, k;  // pigeons i, k
  int j;     // hole j

  if (argc != 2)
    usage();

  n = atoi(argv[1]);

  if (n < 1)
    usage();

  // DIMACS header
  printf("c pigeon-%d: placing %d pigeons into %d holes\n", n, n+1, n);
  printf("c \n");
  printf("c File generated by 'pigeonhole', (c) Tjark Weber\n");
  printf("c \n");
  printf("c The SAT encoding of this problem is very straightforward.  For each pigeon i\n");
  printf("c and each hole j we have a variable x_{n*(i-1)+j} which means that pigeon i\n");
  printf("c is placed in hole j.  Then we have n+1 clauses which say that a pigeon has\n");
  printf("c to be placed in some hole.  Then for each hole we have a set of clauses\n");
  printf("c ensuring that only one single pigeon is placed into that hole.\n");
  printf("c \n");
  printf("c This encoding leads to a total of (n+1) * n propositional variables and\n");
  printf("c (n+1) + n * (n * (n+1) / 2) clauses.\n");
  printf("c \n");
  printf("c The resulting SAT problem is unsatisfiable.\n");
  printf("c \n");
  printf("p cnf %d %d\n", (n+1) * n, (n+1) + n * (n * (n+1) / 2));

  // for each hole we have a set of clauses ensuring that only one single
  // pigeon is placed into that hole
  for (j=1; j <= n; j++)
    for (i=1; i <= n; i++)
      for (k=i+1; k <= n+1; k++)
        printf("-%d -%d 0\n", n*(i-1)+j, n*(k-1)+j);

  // n+1 clauses which say that a pigeon has to be placed in some hole
  for (i=1; i <= n+1; i++) {
    for (j=1; j <= n; j++)
      printf("%d ", n*(i-1)+j);
    printf("0\n");
  }

  return 0;
}
