package serc;
import serc.Model.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Dependency-free acceptance tests. Run with test.sh or test.bat. */
public final class ServiceTest {
    private static int checks;
    private static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    private static void rejects(Runnable work,String label){boolean failed=false;try{work.run();}catch(IllegalArgumentException|DispatchService.SercException ex){failed=true;}check(failed,label);}
    private static DispatchService fresh(){return new DispatchService(new Store.MemoryStore(),Model.seed(),null,false,Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"),ZoneOffset.UTC));}
    private static String report(DispatchService s,EmergencyType t,int severity){return s.report(t,severity,0,0,"Test site","citizen-1");}
    public static void main(String[] args)throws Exception{
        DispatchService s=fresh();String low=report(s,EmergencyType.MEDICAL,4),high=report(s,EmergencyType.MEDICAL,1),tie=report(s,EmergencyType.MEDICAL,1);
        check(s.queue().get(0).id.equals(high),"severity ordering");check(s.queue().get(1).id.equals(tie),"sequence tie-break");
        check(s.dispatchNext().contains(high),"urgent dispatch first");check(s.snapshot().emergencies.get(high).getAssignedTeamId().equals("AMB-1"),"nearest compatible team, ignores police");
        rejects(()->s.resolve(high),"cannot resolve before start");s.start(high);s.resolve(high);
        check(s.snapshot().teams.get("AMB-1").getStatus()==TeamStatus.AVAILABLE,"released team");check(s.snapshot().teams.get("AMB-1").getX()==0,"team moved to incident");rejects(()->s.resolve(high),"no double resolution");
        rejects(()->report(s,EmergencyType.FIRE,0),"invalid severity");rejects(()->s.report(EmergencyType.CRIME,2,Double.NaN,0,"Site","citizen-1"),"NaN rejected");rejects(()->s.start("missing"),"missing id");rejects(()->s.removeTeam("AMB-1"),"history team retained");
        Snapshot fixture=Model.seed();fixture.teams.entrySet().removeIf(e->!e.getKey().equals("AMB-1"));
        DispatchService blocked=new DispatchService(new Store.MemoryStore(),fixture,null,false);String fire=report(blocked,EmergencyType.FIRE,1),medical=report(blocked,EmergencyType.MEDICAL,3);
        check(blocked.dispatchNext().contains(medical),"skip blocked head");check(blocked.queue().get(0).id.equals(fire),"blocked report retained");check(blocked.dispatchNext().contains("No compatible"),"no-team result");
        rejects(()->blocked.removeTeam("AMB-1"),"busy team protected");
        for(int i=0;i<100;i++){
            DispatchService c=new DispatchService(new Store.MemoryStore(),fixture,null,false);report(c,EmergencyType.MEDICAL,1);report(c,EmergencyType.MEDICAL,2);
            CountDownLatch gate=new CountDownLatch(1);List<Throwable> errors=Collections.synchronizedList(new ArrayList<>());
            Runnable work=()->{try{gate.await();c.dispatchNext();}catch(Throwable ex){errors.add(ex);}};
            Thread a=new Thread(work),b=new Thread(work);a.start();b.start();gate.countDown();a.join();b.join();
            check(errors.isEmpty()&&c.snapshot().history.size()==1&&c.queue().size()==1,"concurrent one-team assignment round "+i);
        }
        Path dir=Files.createTempDirectory("serc-test-");Path primary=dir.resolve("state.dat"),backup=dir.resolve("emergency_backup.dat");
        Store file=new Store.FileStore(primary);file.save(s.snapshot());DispatchService restored=new DispatchService(file,file.load(),backup,false);
        check(restored.snapshot().emergencies.size()==3,"restart data");check(restored.queue().get(0).id.equals(tie),"restart queue");restored.saveBackup();check(Backup.load(backup).history.size()==1,"backup roundtrip");
        DispatchService recovery=new DispatchService(new Store.MemoryStore(),Backup.load(backup),null,true);rejects(()->report(recovery,EmergencyType.FIRE,1),"recovery read-only");
        Files.write(backup,new byte[]{1,2,3});boolean corrupt=false;try{Backup.load(backup);}catch(java.io.IOException ex){corrupt=true;}check(corrupt,"corrupt backup fails");
        Store failing=new Store(){public Snapshot load(){return Model.seed();}public void save(Snapshot state)throws Exception{throw new Exception("Injected persistence failure");}};
        DispatchService rollback=new DispatchService(failing,Model.seed(),null,false);rejects(()->report(rollback,EmergencyType.FIRE,1),"save failure surfaced");check(rollback.snapshot().emergencies.isEmpty(),"no memory mutation on failed save");
        Snapshot escaped=s.snapshot();escaped.emergencies.clear();check(s.snapshot().emergencies.size()==3,"snapshot cannot mutate service");
        System.out.println("PASS: "+checks+" checks, including 100 two-thread race tests. No live MySQL test in this suite.");
    }
}
