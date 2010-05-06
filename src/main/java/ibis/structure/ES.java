package ibis.structure;

import java.util.Random;


public class ES {
    public static final class Gene {
        double sigma;
        double alpha;

        public Gene() {
            this.alpha = 1.0;
            this.sigma = 0.1;
        }

        public String toString() {
            return "(α=" + alpha + ", σ=" + sigma + ")";
        }
    }


    private static final int POPULATION_SIZE = 9;
    private static final int TOURNAMENT_SIZE = 3;
    private static final double EPSILON_0 = 0.0001;

    private Gene[] individuals;
    private double[] fitness;
    private Random random;


    public ES() {
        this.individuals = new Gene[POPULATION_SIZE];
        this.fitness = new double[POPULATION_SIZE];
        this.random = new Random(1);

        for (int i = 0; i < POPULATION_SIZE; ++i) {
            this.individuals[i] = new Gene();
            this.fitness[i] = Double.NEGATIVE_INFINITY;
        }
    }

    public synchronized Gene select() {
        Gene mother = individuals[tournamentMax()];
        Gene father = individuals[tournamentMax()];
        Gene child = crossover(mother, father);
        return mutate(child);
    }

    public synchronized void evaluate(Gene child, double fitness_) {
        int next = tournamentMin();
        if (fitness[next] < fitness_) {
            individuals[next] = child;
            fitness[next] = fitness_;
        }
    }

    private int tournamentMin() {
        int best = -1;
        for (int i = 0; i < TOURNAMENT_SIZE; ++i) {
            int index = random.nextInt(POPULATION_SIZE);
            if (i == 0 || fitness[best] > fitness[index])
                best = index;
        }
        return best;
    }

    private int tournamentMax() {
        int best = -1;
        for (int i = 0; i < TOURNAMENT_SIZE; ++i) {
            int index = random.nextInt(POPULATION_SIZE);
            if (i == 0 || fitness[best] < fitness[index])
                best = index;
        }
        return best;
    }

    private Gene crossover(Gene mother, Gene father) {
        Gene child = new Gene();
        child.alpha = (mother.alpha + father.alpha) / 2;
        return child;
    }

    private Gene mutate(Gene child) {
        child.sigma += 0.05 * random.nextGaussian();
        if (child.sigma < EPSILON_0)
            child.sigma = EPSILON_0;

        child.alpha += child.sigma * random.nextGaussian();
        return child;
    }
}
