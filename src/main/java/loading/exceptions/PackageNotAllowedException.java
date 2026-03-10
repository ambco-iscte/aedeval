package loading.exceptions;

import com.github.javaparser.ast.Node;
import extensions.Extensions;

import java.util.Map;

public class PackageNotAllowedException extends Exception {

    private final Map<String, Node[]> usages;
    private final String[] allowed;

    public PackageNotAllowedException(Map<String, Node[]> usages, String[] allowed) {
        StringBuilder builder = new StringBuilder();
        for (String pkg : usages.keySet()) {
            builder.append("Usages of package %s are not allowed (used at: %s)\n".formatted(
                pkg,
                Extensions.joinToString("; ", usages.get(pkg))
            ));
        }
        super(builder.toString());
        this.usages = usages;
        this.allowed = allowed;
    }

    public Map<String, Node[]> getUsages() {
        return usages;
    }

    public String[] getAllowedPackages() {
        return allowed;
    }
}
