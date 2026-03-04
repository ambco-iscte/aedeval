package evaluator.algorithmic;

import java.util.List;

public abstract class OrderOfGrowth {
    abstract double predict(OrderOfGrowthEstimator.Sample sample);

    public static final OrderOfGrowth CONSTANT = new OrderOfGrowth() {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return 1;
        }

        @Override
        public String toString() {
            return "1";
        }
    };

    public static final OrderOfGrowth LOGARITHMIC = new OrderOfGrowth() {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return Math.log(sample.n());
        }

        @Override
        public String toString() {
            return "log(N)";
        }
    };

    public static final OrderOfGrowth LINEAR = new OrderOfGrowth() {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            return sample.n();
        }

        @Override
        public String toString() {
            return "N";
        }
    };

    public static final OrderOfGrowth LINEARITHMIC = new OrderOfGrowth() {
        @Override
        double predict(OrderOfGrowthEstimator.Sample sample) {
            double n = sample.n();
            return n * Math.log(n);
        }

        @Override
        public String toString() {
            return "Nlg(N)";
        }
    };

    public static final OrderOfGrowth QUADRATIC = new OrderOfGrowth() {
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

    public static final OrderOfGrowth CUBIC = new OrderOfGrowth() {
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

    public static final OrderOfGrowth QUARTIC = new OrderOfGrowth() {
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

    public static final OrderOfGrowth QUINTIC = new OrderOfGrowth() {
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

    public static final OrderOfGrowth EXPONENTIAL = new OrderOfGrowth() {
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
        return new OrderOfGrowth() {
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
        return new OrderOfGrowth() {
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
        return new OrderOfGrowth() {
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
        return new OrderOfGrowth() {
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

    public static final OrderOfGrowth[] BASIS = new OrderOfGrowth[] {
        CONSTANT, LOGARITHMIC, LINEAR, LINEARITHMIC, QUADRATIC, CUBIC, QUARTIC, QUINTIC, EXPONENTIAL
    };
}
