import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

// Синтаксис Java 21, НЕ типизация NeoForge и НЕ проверка mixin-targets.
class JavaSyntaxCheck {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8);
             var files = Files.walk(Path.of(args[0]))) {
            var paths = files.filter(p -> p.toString().endsWith(".java")).toList();
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none"), null, manager.getJavaFileObjectsFromPaths(paths));
            task.parse();
            for (var d : diagnostics.getDiagnostics()) if (d.getKind() == Diagnostic.Kind.ERROR)
                throw new IllegalStateException(d.toString());
            System.out.println("PASS: Java 21 syntax, " + paths.size() + " source files (not an integration compilation)");
        }
    }
}
