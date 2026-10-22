package serc;
import serc.Model.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class Main {
    private static final Scanner INPUT=new Scanner(System.in);
    private static DispatchService service;
    private static String ask(String text){System.out.print(text+": ");if(!INPUT.hasNextLine())throw new NoSuchElementException("Input ended");return INPUT.nextLine().trim();}
    private static int number(String text){return Integer.parseInt(ask(text));}
    private static double coord(String text){return Double.parseDouble(ask(text));}
    private static void display(Collection<?> items){if(items.isEmpty())System.out.println("None.");else items.forEach(System.out::println);}
    public static void main(String[] args){
        Set<String> flags=new HashSet<>(Arrays.asList(args));
        if(flags.contains("--help")){System.out.println("java -jar serc.jar [--file | --mysql | --recover] [--init-demo]\nDefault --file stores demo state locally. --mysql uses SERC_DB_* environment values. --recover opens backup read-only.");return;}
        try{
            Path backup=Paths.get("data/emergency_backup.dat");
            boolean recovery=flags.contains("--recover");Store store;
            Snapshot initial;
            if(recovery){store=new Store.MemoryStore();initial=Backup.load(backup);System.out.println("READ-ONLY BACKUP RECOVERY. Snapshot: "+initial.savedAt);}
            else{
                store=flags.contains("--mysql")?new JdbcStore(System.getenv("SERC_DB_URL"),System.getenv("SERC_DB_USER"),System.getenv("SERC_DB_PASSWORD")):new Store.FileStore(Paths.get("data/serc_state.dat"));
                initial=store.load();
                if(initial.users.isEmpty()&&(flags.contains("--init-demo")||!flags.contains("--mysql"))){initial=Model.seed();store.save(initial);}
                if(initial.users.isEmpty())throw new IllegalStateException("Database has no users. Start once with --mysql --init-demo or run seed.sql.");
                System.out.println(flags.contains("--mysql")?"Mode: MySQL (transactional snapshot store).":"Mode: LOCAL FILE DEMO (not MySQL). Use --mysql for JDBC.");
            }
            try(Store opened=store){service=new DispatchService(opened,initial,backup,recovery);if(!recovery)service.saveBackup();
                System.out.println("SERC - classroom simulation. Severity 1 is highest. Role selection is NOT authentication.");
                boolean running=true;while(running){try{
                    System.out.println("\n1 Citizen | 2 Dispatcher | 3 Admin | 0 Exit");
                    switch(number("Role")){case 1:citizen();break;case 2:dispatcher();break;case 3:admin();break;case 0:running=false;break;default:System.out.println("Choose 0-3.");}
                    showWarning();
                }catch(NumberFormatException ex){System.out.println("Enter a valid number.");}catch(IllegalArgumentException|DispatchService.SercException ex){System.out.println("Error: "+ex.getMessage());}}
                service.saveBackup();
            }
        }catch(NoSuchElementException ex){System.out.println("Input ended. Every confirmed change was already saved.");}
        catch(Exception ex){System.err.println("Startup/exit failed: "+ex.getMessage()+"\nNo automatic fallback was made. Fix the primary store or restart with --recover for read-only backup viewing.");System.exit(1);}
    }
    private static void showWarning(){if(!service.warning().isEmpty())System.out.println("WARNING: "+service.warning());}
    private static void citizen(){
        service.snapshot().users.values().stream().filter(u->u.role==Role.CITIZEN).forEach(u->System.out.println(u.id+" | "+u.name));
        String user=ask("Citizen ID");User u=service.snapshot().users.get(user);if(u==null||u.role!=Role.CITIZEN)throw new IllegalArgumentException("Unknown citizen");
        System.out.println("1 Report emergency | 2 View my reports | 0 Back");
        switch(number("Action")){case 1:
            EmergencyType type=EmergencyType.valueOf(ask("Type MEDICAL/FIRE/CRIME").toUpperCase(Locale.ROOT));
            int severity=number("Severity 1-5 (1 highest)");double x=coord("X"),y=coord("Y");String location=ask("Location label");
            System.out.println("Saved report: "+service.report(type,severity,x,y,location,user));break;
            case 2:display(service.reports(user));break;case 0:break;default:System.out.println("Unknown action");}
    }
    private static void dispatcher(){
        System.out.println("1 View priority queue | 2 Assign nearest team | 3 Track active responses | 4 Start response | 5 Resolve response | 0 Back");
        switch(number("Action")){case 1:display(service.queue());break;case 2:System.out.println(service.dispatchNext());break;
            case 3:display(service.snapshot().emergencies.values().stream().filter(e->e.getStatus()==Status.ASSIGNED||e.getStatus()==Status.IN_PROGRESS).sorted(DispatchService.ORDER).collect(java.util.stream.Collectors.toList()));break;
            case 4:service.start(ask("Emergency ID"));System.out.println("Response started.");break;
            case 5:service.resolve(ask("Emergency ID"));System.out.println("Resolved. Team is available at incident coordinates.");break;
            case 0:break;default:System.out.println("Unknown action");}
    }
    private static void admin(){
        System.out.println("1 List teams | 2 Add team | 3 Remove unused team | 4 View history | 5 Generate report | 6 Save backup | 7 Concurrent dispatch demo | 0 Back");
        switch(number("Action")){case 1:display(service.snapshot().teams.values());break;
            case 2:String id=ask("Team ID");TeamType type=TeamType.valueOf(ask("AMBULANCE/FIRE_TRUCK/POLICE").toUpperCase(Locale.ROOT));service.addTeam(id,type,coord("X"),coord("Y"));System.out.println("Team saved.");break;
            case 3:service.removeTeam(ask("Team ID"));System.out.println("Team removed.");break;
            case 4:display(service.snapshot().history);break;
            case 5:Snapshot s=service.snapshot();for(Status status:Status.values())System.out.println(status+": "+s.emergencies.values().stream().filter(e->e.getStatus()==status).count());for(EmergencyType t:EmergencyType.values())System.out.println(t+": "+s.emergencies.values().stream().filter(e->e.type==t).count());break;
            case 6:service.saveBackup();System.out.println("Backup save requested; recovery mode does not write.");break;
            case 7:concurrentDemo();break;case 0:break;default:System.out.println("Unknown action");}
    }
    /** Isolated fixture: does not alter the user's current simulation records. */
    private static void concurrentDemo(){
        Snapshot s=Model.seed();s.teams.entrySet().removeIf(e->!e.getKey().equals("AMB-1"));
        DispatchService demo=new DispatchService(new Store.MemoryStore(),s,null,false);
        demo.report(EmergencyType.MEDICAL,1,0,0,"Demo A","citizen-1");demo.report(EmergencyType.MEDICAL,2,0,0,"Demo B","citizen-1");
        CountDownLatch gate=new CountDownLatch(1);List<Throwable> errors=Collections.synchronizedList(new ArrayList<>());
        Runnable worker=()->{try{gate.await();System.out.println(Thread.currentThread().getName()+": "+demo.dispatchNext());}catch(Throwable e){errors.add(e);}};
        Thread a=new Thread(worker,"worker-1"),b=new Thread(worker,"worker-2");a.start();b.start();gate.countDown();
        try{a.join();b.join();}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException("Demo interrupted",ex);}
        Snapshot result=demo.snapshot();if(!errors.isEmpty()||result.history.size()!=1||demo.queue().size()!=1)throw new IllegalStateException("Concurrency assertion failed");
        System.out.println("PASS: exactly one assignment and one waiting report. Main records unchanged.");
    }
}
