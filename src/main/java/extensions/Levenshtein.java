package extensions;

/**
 * Calculates the Levenshtein Distance between two strings from a given set of cost parameters.
 */
public class Levenshtein {

    private double insertion;
    private double deletion;
    private double substitution;
    private double capitalisation;

    public Levenshtein() {
        this.insertion = 1.0;
        this.deletion = 1.0;
        this.substitution = 2.0;
        this.capitalisation = 0.5;
    }

    public Levenshtein(double insertion, double deletion, double substitution, double capitalisation) {
        this.insertion = insertion;
        this.deletion = deletion;
        this.substitution = substitution;
        this.capitalisation = capitalisation;
    }

    public double getInsertionCost() {
        return insertion;
    }

    public double getDeletionCost() {
        return deletion;
    }

    public double getSubstitutionCost() {
        return substitution;
    }

    public double getCapitalisationCost() {
        return capitalisation;
    }

    public void setInsertionCost(double cost) {
        this.insertion = cost;
    }

    public void setDeletionCost(double cost) {
        this.deletion = cost;
    }

    public void setSubstitutionCost(double cost) {
        this.substitution = cost;
    }

    public void setCapitalisationCost(double cost) {
        this.capitalisation = cost;
    }

    private double sub(char x, char y) {
        if (x == y)
            return 0.0;
        if (Character.toLowerCase(x) == Character.toLowerCase(y))
            return capitalisation;
        return substitution;
    }

    public boolean similar(String x, String y, double ratio) {
        return distance(x, y) <= Math.round(ratio * Math.max(x.length(), y.length()));
    }

    /**
     * Returns the Levenshtein distance between two Strings.
     */
    public double distance(String x, String y) {
        int m = x.length();
        int n = y.length();

        double[][] d = new double[m + 1][n + 1];

        for (int i = 1; i <= m; i++)
            d[i][0] = i;

        for (int j = 1; j <= n; j++)
            d[0][j] = j;

        for (int j = 1; j <= n; j++) {
            for (int i = 1; i <= m; i++) {
                d[i][j] = Extensions.min(
                        d[i - 1][j] + deletion,
                        d[i][j - 1] + insertion,
                        d[i - 1][j - 1] + sub(x.charAt(i - 1), y.charAt(j - 1))
                );
            }
        }

        return d[m][n];
    }
}
