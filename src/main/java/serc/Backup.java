package serc;
import java.io.*;
import java.nio.file.*;
import serc.Model.Snapshot;

public final class Backup {
    private Backup() {}
    private static byte[] bytes(Snapshot s)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(ObjectOutputStream out=new ObjectOutputStream(b)){out.writeObject(s);}
        return b.toByteArray();
    }
    private static ObjectInputStream input(InputStream in)throws IOException{
        ObjectInputStream stream=new ObjectInputStream(in);
        stream.setObjectInputFilter(info->{
            if(info.depth()>30||info.references()>100000||info.streamBytes()>20_000_000||info.arrayLength()>100000)
                return ObjectInputFilter.Status.REJECTED;
            Class<?> c=info.serialClass();
            if(c==null)return ObjectInputFilter.Status.UNDECIDED;
            while(c.isArray())c=c.getComponentType();
            String n=c.getName();
            return c.isPrimitive()||n.startsWith("serc.Model$")||n.equals("java.lang.String")||n.equals("java.lang.Enum")||n.startsWith("java.time.")||n.equals("java.util.HashMap")||n.equals("java.util.ArrayList")||n.equals("java.util.Map$Entry")||n.equals("java.lang.Object")
                ? ObjectInputFilter.Status.ALLOWED:ObjectInputFilter.Status.REJECTED;
        });
        return stream;
    }
    static Snapshot copy(Snapshot s){
        try(ObjectInputStream in=input(new ByteArrayInputStream(bytes(s)))){return (Snapshot)in.readObject();}
        catch(IOException|ClassNotFoundException ex){throw new IllegalStateException("Cannot copy state",ex);}
    }
    public static void save(Path path,Snapshot s)throws IOException{
        Path target=path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temp=Files.createTempFile(target.getParent(),"serc-", ".tmp");
        try{
            Files.write(temp,bytes(s));
            try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    public static Snapshot load(Path path)throws IOException{
        try(ObjectInputStream in=input(Files.newInputStream(path))){
            Object o=in.readObject();
            if(!(o instanceof Snapshot))throw new IOException("Not a SERC snapshot");
            Snapshot s=(Snapshot)o; DispatchService.validate(s);return s;
        }catch(ClassNotFoundException|RuntimeException ex){throw new IOException("Invalid backup: "+ex.getMessage(),ex);}
    }
}
