package joelbits.collectors.spi;

public interface DataCollector {
    void collect(String repositoryPath);
    String type();
}
