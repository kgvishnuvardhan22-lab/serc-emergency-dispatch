package serc;
import serc.Model.Snapshot;
import java.io.IOException;
import java.nio.file.*;

public interface Store extends AutoCloseable {
    Snapshot load() throws Exception;
    void save(Snapshot snapshot) throws Exception;
    default void close() throws Exception {}
    final class FileStore implements Store {
        private final Path path;
        public FileStore(Path path){this.path=path;}
        public Snapshot load()throws IOException{return Files.exists(path)?Backup.load(path):new Snapshot();}
        public void save(Snapshot s)throws IOException{Backup.save(path,s);}
    }
    final class MemoryStore implements Store {
        private Snapshot data=new Snapshot();
        public Snapshot load(){return Backup.copy(data);}
        public void save(Snapshot s){data=Backup.copy(s);}
    }
}
