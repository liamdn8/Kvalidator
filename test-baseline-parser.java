import com.nfv.validator.yaml.YamlDataCollector;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;

public class TestBaselineParser {
    public static void main(String[] args) throws Exception {
        YamlDataCollector collector = new YamlDataCollector();
        FlatNamespaceModel ns = collector.collectFromYaml("baseline-design.yaml", "baseline");
        
        for (String key : ns.getObjects().keySet()) {
            FlatObjectModel obj = ns.getObjects().get(key);
            System.out.println("\n=== " + obj.getKind() + "/" + obj.getName() + " ===");
            System.out.println("Metadata fields:");
            if (obj.getMetadata() != null) {
                obj.getMetadata().forEach((k, v) -> System.out.println("  " + k + ": " + v));
            }
            System.out.println("Spec fields (first 5):");
            if (obj.getSpec() != null) {
                obj.getSpec().entrySet().stream().limit(5).forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));
            }
        }
    }
}
