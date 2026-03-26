package evaluator.algorithmic;

import extensions.Extensions;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public abstract class OrderOfGrowth implements Comparable<OrderOfGrowth> {

    public enum Family {
        EXPONENTIAL,
        POLYLOGARITHMIC,
        POLYNOMIAL,
        LOGARITHMIC,
    }

    private record Signature(int expCoeff, int expPow, int poly, int log, int iteratedLog) implements Comparable<Signature> {

        private static final String NO_NEG_EXP = "Growth order signatures cannot have negative exponents!";

        private Family determineFamily() {
            if (expCoeff > 0 && expPow > 0) return Family.EXPONENTIAL;
            else if (poly > 0 && log > 0) return Family.POLYLOGARITHMIC;
            else if (poly > 0 && log == 0) return Family.POLYNOMIAL;
            else if (log > 0 || iteratedLog > 0) return Family.LOGARITHMIC;
            else return null;
        }

        public static double distance(Signature s1, Signature s2) {
            if (s1 == null || s2 == null)
                throw new IllegalArgumentException("");
            double dist = 0.0;

            int[] powers1 = new int[] { s1.iteratedLog, s1.log, s1.poly, s1.expCoeff, s1.expPow };
            int[] powers2 = new int[] { s2.iteratedLog, s2.log, s2.poly, s2.expCoeff, s2.expPow };

            for (int i = 0; i < powers1.length; i++)
                dist += (1 << i) * Math.abs(powers1[i] - powers2[i]);

            return dist;
        }

        public static double similarity(Signature s1, Signature s2) {
            return 1 / (1 + distance(s1, s2));
        }

        private Signature normalized() {
            if (expCoeff == 0 || expPow == 0)
                return new Signature(0, 0, poly, log, iteratedLog);
            return this;
        }

        @Override
        public int compareTo(@NotNull Signature o) {
            // Compare Exponential Powers
            if (expPow != o.expPow)
                return Integer.compare(expPow, o.expPow); // Different Powers -> Larger Power Wins
            else if (expPow != 0 && expCoeff != o.expCoeff)
                return Integer.compare(expCoeff, o.expCoeff); // Same Powers -> Larger Coefficient Wins

            // Compare Polynomial Exponents
            if (poly != o.poly)
                return Integer.compare(poly, o.poly);

            // Compare Log Exponents
            if (log != o.log)
                return Integer.compare(log, o.log);

            // Compare LogLog Exponents
            return Integer.compare(iteratedLog, o.iteratedLog);
        }

        private Signature times(Signature other) {
            int a0, exp0;
            if (expPow == other.expPow) {
                exp0 = expPow;
                a0 = expCoeff + other.expCoeff;
            } else if (expPow > other.expPow) {
                exp0 = expPow;
                a0 = expCoeff;
            } else {
                exp0 = other.expPow;
                a0 = other.expCoeff;
            }
            return new Signature(a0, exp0, poly + other.poly, log + other.log, iteratedLog + other.iteratedLog).normalized();
        }

        private Signature div(Signature other) {
            int a0 = 0, exp0 = 0;
            if (expPow != 0 || other.expPow != 0) {
                if (expPow == other.expPow) {
                    exp0 = expPow;
                    a0 = expCoeff - other.expCoeff;
                    if (a0 < 0)
                        throw new IllegalArgumentException(NO_NEG_EXP);
                } else if (expPow > other.expPow) {
                    exp0 = expPow;
                    a0 = expCoeff;
                } else
                    throw new IllegalArgumentException(NO_NEG_EXP);
            }

            int poly0 = poly - other.poly;
            int log0 = log - other.log;
            int loglog0 = iteratedLog - other.iteratedLog;

            if (poly0 < 0 || log0 < 0 || loglog0 < 0)
                throw new IllegalArgumentException(NO_NEG_EXP);

            return new Signature(a0, exp0, poly0, log0, loglog0).normalized();
        }

        @Override
        public @NotNull String toString() {
            return "exp(%d * N^%d) * N^%d * (logN)^%d * (loglogN)^%d".formatted(expCoeff, expPow, poly, log, iteratedLog);
        }
    }

    private final Signature signature;
    public final Family family;

    private OrderOfGrowth(Signature signature) {
        this.signature = signature.normalized();
        this.family = this.signature.determineFamily();
    }

    private OrderOfGrowth(int a, int exp, int poly, int log, int iteratedLog) {
        this.signature = new Signature(a, exp, poly, log, iteratedLog).normalized();
        this.family = this.signature.determineFamily();
    }

    @Override
    public int compareTo(@NonNull OrderOfGrowth o) {
        return signature.compareTo(o.signature);
    }

    public static double similarity(OrderOfGrowth o1, OrderOfGrowth o2) {
        return Signature.similarity(o1.signature, o2.signature);
    }

    abstract double predict(OrderOfGrowthEstimator.Sample sample);

    public static final OrderOfGrowth CONSTANT = new OrderOfGrowth(0, 0, 0, 0, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return 1;
        }

        @Override
        public String toString() {
            return "1";
        }
    };

    public static final OrderOfGrowth LOGSTAR = new OrderOfGrowth(0, 0, 0, 0, 1) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return Extensions.iteratedLogarithm(sample.n());
        }

        @Override
        public String toString() {
            return "log*(N)";
        }
    };

    public static final OrderOfGrowth LOGARITHMIC = new OrderOfGrowth(0, 0, 0, 1, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return Math.log(sample.n());
        }

        @Override
        public String toString() {
            return "log(N)";
        }
    };

    public static final OrderOfGrowth LINEAR = new OrderOfGrowth(0, 0, 1, 0, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return sample.n();
        }

        @Override
        public String toString() {
            return "N";
        }
    };

    public static final OrderOfGrowth LINEARLOGSTAR = new OrderOfGrowth(0, 0, 1, 0, 1) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return sample.n() * Extensions.iteratedLogarithm(sample.n());
        }

        @Override
        public String toString() {
            return "N log*(N)";
        }
    };

    public static final OrderOfGrowth LINEARITHMIC = new OrderOfGrowth(0, 0, 1, 1, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            double n = sample.n();
            return n * Math.log(n);
        }

        @Override
        public String toString() {
            return "N log(N)";
        }
    };

    public static final OrderOfGrowth QUADRATIC = new OrderOfGrowth(0, 0, 2, 0, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            double n = sample.n();
            return n * n;
        }

        @Override
        public String toString() {
            return "N²";
        }
    };

    public static final OrderOfGrowth CUBIC = new OrderOfGrowth(0, 0, 3, 0, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            double n = sample.n();
            return n * n * n;
        }

        @Override
        public String toString() {
            return "N³";
        }
    };

    public static final OrderOfGrowth QUARTIC = new OrderOfGrowth(0, 0, 4, 0, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            double n = sample.n();
            return n * n * n * n;
        }

        @Override
        public String toString() {
            return "N⁴";
        }
    };

    public static final OrderOfGrowth QUINTIC = new OrderOfGrowth(0, 0, 5, 0, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            double n = sample.n();
            return n * n * n * n * n;
        }

        @Override
        public String toString() {
            return "N⁵";
        }
    };

    public static final OrderOfGrowth EXPONENTIAL = new OrderOfGrowth(1, 1, 0, 0, 0) {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return Math.exp(sample.n());
        }

        @Override
        public String toString() {
            return "exp(N)";
        }
    };

    public double[] predict(List<OrderOfGrowthEstimator.Sample> samples) {
        if (samples == null || samples.isEmpty())
            throw new IllegalArgumentException("List of samples must be non-null and non-empty!");
        double[] predictions = new double[samples.size()];
        for (int i = 0; i < predictions.length; i++)
            predictions[i] = predict(samples.get(i));
        return predictions;
    }

    public OrderOfGrowth plus(OrderOfGrowth other) {
        OrderOfGrowth self = this;
        return new OrderOfGrowth(Extensions.max(self.signature, other.signature)) {
            @Override
            double predict(OrderOfGrowthEstimator.Sample sample) {
                return self.predict(sample) + other.predict(sample);
            }

            @Override
            public String toString() {
                return String.format("(%s) + (%s)", self, other);
            }
        };
    }

    public OrderOfGrowth minus(OrderOfGrowth other) {
        OrderOfGrowth self = this;
        return new OrderOfGrowth(Extensions.max(self.signature, other.signature)) {
            @Override
            double predict(OrderOfGrowthEstimator.Sample sample) {
                return self.predict(sample) - other.predict(sample);
            }

            @Override
            public String toString() {
                return String.format("(%s) - (%s)", self, other);
            }
        };
    }

    public OrderOfGrowth times(OrderOfGrowth other) {
        OrderOfGrowth self = this;
        return new OrderOfGrowth(self.signature.times(other.signature)) {
            @Override
            double predict(OrderOfGrowthEstimator.Sample sample) {
                return self.predict(sample) * other.predict(sample);
            }

            @Override
            public String toString() {
                return String.format("(%s) * (%s)", self, other);
            }
        };
    }

    public OrderOfGrowth div(OrderOfGrowth other) {
        OrderOfGrowth self = this;
        return new OrderOfGrowth(self.signature.div(other.signature)) {
            @Override
            double predict(OrderOfGrowthEstimator.Sample sample) {
                return self.predict(sample) / other.predict(sample);
            }

            @Override
            public String toString() {
                return String.format("(%s) / (%s)", self, other);
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        OrderOfGrowth that = (OrderOfGrowth) o;
        return Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(signature);
    }

    public static final OrderOfGrowth[] BASIS = new OrderOfGrowth[] {
        CONSTANT, LOGARITHMIC, LINEAR, EXPONENTIAL,
    };

    public static final OrderOfGrowth[] COMMON = new OrderOfGrowth[] {
        CONSTANT, LOGARITHMIC, LINEAR, LINEARITHMIC, QUADRATIC, CUBIC, QUARTIC
    };

    public static final OrderOfGrowth[] EXTENDED = new OrderOfGrowth[] {
        CONSTANT, LOGSTAR, LOGARITHMIC, LINEAR, LINEARLOGSTAR, LINEARITHMIC, QUADRATIC, CUBIC, QUARTIC, QUINTIC, EXPONENTIAL,
        QUADRATIC.times(LOGARITHMIC), CUBIC.times(LOGARITHMIC), QUARTIC.times(LOGARITHMIC), QUINTIC.times(LOGARITHMIC)
    };
}
