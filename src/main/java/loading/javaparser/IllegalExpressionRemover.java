package loading.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import extensions.Extensions;
import loading.Source;
import loading.exceptions.UnsupportedJavaFeatureException;

public class IllegalExpressionRemover extends ModifierVisitor<Void> {

    private final String[] allowedPackages;

    public IllegalExpressionRemover(String[] allowedPackages) {
        this.allowedPackages = allowedPackages;
    }

    private boolean isProhibitedField(FieldAccessExpr field) {
        try {
            String pkg = field.resolve().asField().declaringType().getPackageName();
            return !pkg.isEmpty() && !Extensions.contains(allowedPackages, pkg);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isProhibitedType(Type type) {
        try {
            return isProhibitedType(type.resolve());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isProhibitedType(ResolvedType type) {
        if (type.isArray())
            return isProhibitedType(type.asArrayType().getComponentType());
        if (type.isReferenceType()) {
            ResolvedReferenceTypeDeclaration owner = type.asReferenceType().getTypeDeclaration().orElse(null);
            return owner != null && !owner.getPackageName().isEmpty() && !Extensions.contains(allowedPackages, owner.getPackageName());
        }
        return false;
    }

    private boolean isProhibitedType(ClassOrInterfaceType type) {
        try {
            return isProhibitedType(type.resolve());
        } catch (Exception ignored) { }
        return false;
    }

    private boolean isProhibitedTypeName(NameExpr name) {
        try {
            return isProhibitedType(name.calculateResolvedType());
        } catch (Exception ignored) { }
        return false;
    }

    private boolean isProhibitedExpression(Expression expression) {
        if (expression == null)
            return false;
        return
            (expression.isFieldAccessExpr() && isProhibitedField(expression.asFieldAccessExpr())) ||
            (expression.isNameExpr() && isProhibitedTypeName(expression.asNameExpr()));
    }

    @Override
    public Visitable visit(MethodCallExpr n, Void arg) {
        if (isProhibitedExpression(n.getScope().orElse(null)))
            return Source.removed(n);
        else {
            for (Expression argument : n.getArguments()) {
                if (isProhibitedExpression(argument))
                    return Source.removed(n);
            }
        }
        return super.visit(n, arg);
    }

    @Override
    public Visitable visit(ObjectCreationExpr n, Void arg) {
        if (isProhibitedType(n.getType()) || isProhibitedExpression(n.getScope().orElse(null)))
            return Source.removed(n);
        else {
            for (Expression argument : n.getArguments()) {
                if (isProhibitedExpression(argument))
                    return Source.removed(n);
            }
        }
        return super.visit(n, arg);
    }

    /*
    @Override
    public Visitable visit(VariableDeclarator n, Void arg) {
        if (isProhibitedExpression(n.getInitializer().orElse(null)) || isProhibitedType(n.getType()))
            n.removeInitializer();
        return super.visit(n, arg);
    }
     */

    static void main() throws UnsupportedJavaFeatureException {
        String src = """
            public class Date {
                
                                           private int month;
                                           private int day;
                                           private int year;
                
                                           public Date(int month, int day, int year) {
                                               this.month = month;
                                               this.day = day;
                                               this.year = year;
                                           }
                
                                           public int month() {
                                               return month;
                                           }
                
                                           public int day() {
                                               return day;
                                           }
                
                                           public int year() {
                                               return year;
                                           }
                
                                           public String toString() {
                                               return day + "/" + month + "/" + year;
                                           }
                
                                           public boolean before(Date other) {
                
                                               if (year != other.year()) {
                                                   return year < other.year();
                                               }
                
                                               if (month != other.month()) {
                                                   return month < other.month();
                                               }
                
                                               return day < other.day();
                                           }
                
                                           private boolean leap(int y) {
                
                                               if (y % 400 == 0) return true;
                                               if (y % 100 == 0) return false;
                                               return y % 4 == 0;
                                           }
                
                                           private int totalDaysInYear(int y) {
                                               if (leap(y)) return 366;
                                               return 365;
                                           }
                
                                           private int daysInMonth(int m, int y) {
                
                                               int[] days = {31,28,31,30,31,30,31,31,30,31,30,31};
                
                                               if (m == 2 && leap(y)) {
                                                   return 29;
                                               }
                
                                               return days[m - 1];
                                           }
                
                                           public int daysSinceBeginYear() {
                
                                               int sum = 0;
                
                                               for (int i = 1; i < month; i++) {
                                                   sum += daysInMonth(i, year);
                                               }
                
                                               sum += day;
                
                                               return sum;
                                           }
                
                                           public int daysUntilEndYear() {
                                               return totalDaysInYear(year) - daysSinceBeginYear();
                                           }
                
                                           public int daysBetween(Date other) {
                
                                               if (year == other.year()) {
                                                   return Math.abs(daysSinceBeginYear() - other.daysSinceBeginYear());
                                               }
                
                                               Date start = this;
                                               Date end = other;
                
                                               if (!this.before(other)) {
                                                   start = other;
                                                   end = this;
                                               }
                
                                               int result = 0;
                
                                               result += totalDaysInYear(start.year()) - start.daysSinceBeginYear();
                
                                               for (int y = start.year() + 1; y < end.year(); y++) {
                                                   result += totalDaysInYear(y);
                                               }
                
                                               result += end.daysSinceBeginYear();
                
                                               return result;
                                           }
                
                                       }
                
        """;
        CompilationUnit unit = StaticJavaParser.parse(src);
        unit = Source.clean(unit, new String[] {"java.lang", "java.util"});
        System.out.println(unit);
    }
}
