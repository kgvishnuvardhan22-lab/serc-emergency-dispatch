package serc;
import serc.Model.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public final class DispatchService {
    public static class SercException extends RuntimeException {
        private static final long serialVersionUID=1L;
        SercException(String message){super(message);}
    }
    public static final class InvalidSeverityException extends SercException {
        private static final long serialVersionUID=1L;
        InvalidSeverityException(){super("Severity must be 1 (highest) to 5 (lowest).");}
    }
    public static final class EmergencyNotFoundException extends SercException {
        private static final long serialVersionUID=1L;
        EmergencyNotFoundException(String id){super("Unknown emergency: "+id);}
    }
    public static final Comparator<Emergency> ORDER=Comparator.comparingInt(Emergency::getSeverity)
        .thenComparing(Emergency::getReportedAt).thenComparingLong(Emergency::getSequence);
    private final Object lock=new Object();
    private Snapshot state;
    private PriorityQueue<Emergency> waiting=new PriorityQueue<>(ORDER);
    private final Store store;
    private final Path backup;
    private final boolean readOnly;
    private final Clock clock;
    private String warning="";
    private boolean uncertain=false;
    public DispatchService(Store store,Snapshot initial,Path backup,boolean readOnly){this(store,initial,backup,readOnly,Clock.systemDefaultZone());}
    DispatchService(Store store,Snapshot initial,Path backup,boolean readOnly,Clock clock){
        validate(initial);this.store=store;this.state=Backup.copy(initial);this.backup=backup;this.readOnly=readOnly;this.clock=clock;rebuild();
    }
    private void rebuild(){waiting=new PriorityQueue<>(ORDER);state.emergencies.values().stream().filter(e->e.getStatus()==Status.REPORTED).forEach(waiting::offer);}
    private void writable(){if(readOnly)throw new SercException("Backup recovery is read-only. Restart with the primary store to write.");if(uncertain)throw new SercException("Store outcome uncertain. Restart and reconcile before more writes.");}
    private void commit(Snapshot next){
        writable();next.savedAt=LocalDateTime.now(clock);validate(next);
        try{store.save(next);}catch(Exception ex){
            if(store instanceof JdbcStore)uncertain=true;
            throw new SercException("Save failed; no success reported. "+ex.getMessage());
        }
        state=next;rebuild();warning="";
        if(backup!=null){try{Backup.save(backup,state);}catch(Exception ex){warning="Primary save succeeded, but backup failed: "+ex.getMessage();}}
    }
    public Snapshot snapshot(){synchronized(lock){return Backup.copy(state);}}
    public String warning(){synchronized(lock){return warning;}}
    public List<Emergency> queue(){synchronized(lock){Snapshot copy=Backup.copy(state);return copy.emergencies.values().stream().filter(e->e.getStatus()==Status.REPORTED).sorted(ORDER).collect(Collectors.toList());}}
    public List<Emergency> reports(String user){return snapshot().emergencies.values().stream().filter(e->e.reportedBy.equals(user)).sorted(ORDER).collect(Collectors.toList());}
    public String report(EmergencyType type,int severity,double x,double y,String location,String user){
        synchronized(lock){writable();if(severity<1||severity>5)throw new InvalidSeverityException();coordinates(x,y);
            if(type==null||location==null||location.trim().isEmpty()||location.length()>200)throw new SercException("Type and a location of 1-200 characters are required.");
            User reporter=state.users.get(user);if(reporter==null||reporter.role!=Role.CITIZEN)throw new SercException("Choose an existing citizen.");
            Snapshot next=Backup.copy(state);String id=UUID.randomUUID().toString();
            next.emergencies.put(id,Model.emergency(id,type,severity,x,y,location.trim(),user,LocalDateTime.now(clock),next.nextSequence++));commit(next);return id;
        }
    }
    public String dispatchNext(){
        synchronized(lock){writable();
            // A copy lets us skip blocked types without losing waiting reports.
            PriorityQueue<Emergency> candidates=new PriorityQueue<>(waiting);
            while(!candidates.isEmpty()){
                Emergency e=candidates.poll();
                ResponseTeam team=state.teams.values().stream().filter(t->t.getStatus()==TeamStatus.AVAILABLE&&t.type==e.requiredTeamType())
                    .min(Comparator.comparingDouble((ResponseTeam t)->Math.hypot(t.getX()-e.x,t.getY()-e.y)).thenComparing(t->t.id)).orElse(null);
                if(team==null)continue;
                Snapshot next=Backup.copy(state);Emergency selected=next.emergencies.get(e.id);ResponseTeam chosen=next.teams.get(team.id);
                selected.setState(Status.ASSIGNED,chosen.id);chosen.setState(TeamStatus.BUSY,chosen.getX(),chosen.getY());
                next.history.add(new ResponseHistory(UUID.randomUUID().toString(),e.id,team.id,LocalDateTime.now(clock)));commit(next);
                return "Assigned "+e.id+" to "+team.id;
            }
            return waiting.isEmpty()?"No waiting reports.":"No compatible team available; all reports remain waiting.";
        }
    }
    public void start(String id){transition(id,false);}
    public void resolve(String id){transition(id,true);}
    private void transition(String id,boolean resolve){synchronized(lock){writable();Emergency current=state.emergencies.get(id);if(current==null)throw new EmergencyNotFoundException(id);
        Status expected=resolve?Status.IN_PROGRESS:Status.ASSIGNED;
        if(current.getStatus()!=expected)throw new SercException("Expected "+expected+", found "+current.getStatus());
        Snapshot next=Backup.copy(state);Emergency e=next.emergencies.get(id);
        ResponseHistory h=next.history.stream().filter(r->r.emergencyId.equals(id)).findFirst().orElseThrow(()->new SercException("Missing history"));
        LocalDateTime now=LocalDateTime.now(clock);
        if(resolve){e.setState(Status.RESOLVED,e.getAssignedTeamId());h.setTimes(h.getStartedAt(),now);next.teams.get(e.getAssignedTeamId()).setState(TeamStatus.AVAILABLE,e.x,e.y);}
        else{e.setState(Status.IN_PROGRESS,e.getAssignedTeamId());h.setTimes(now,null);}commit(next);
    }}
    public void addTeam(String id,TeamType type,double x,double y){synchronized(lock){writable();coordinates(x,y);
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,36}")||state.teams.containsKey(id)||type==null)throw new SercException("Use a unique team ID of 1-36 letters, digits, _ or -.");
        Snapshot next=Backup.copy(state);next.teams.put(id,new ResponseTeam(id,type,x,y));commit(next);
    }}
    public void removeTeam(String id){synchronized(lock){writable();ResponseTeam t=state.teams.get(id);if(t==null)throw new SercException("Unknown team");
        if(t.getStatus()==TeamStatus.BUSY||state.history.stream().anyMatch(h->h.teamId.equals(id)))throw new SercException("Cannot delete a busy team or a team referenced by history.");
        Snapshot next=Backup.copy(state);next.teams.remove(id);commit(next);
    }}
    public void saveBackup(){synchronized(lock){if(backup==null||readOnly||uncertain)return;try{Backup.save(backup,state);}catch(Exception e){throw new SercException("Backup failed: "+e.getMessage());}}}
    private static void coordinates(double x,double y){if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>1_000_000||Math.abs(y)>1_000_000)throw new SercException("Use finite coordinates between -1000000 and 1000000.");}
    public static void validate(Snapshot s){
        if(s==null||s.formatVersion!=1||s.savedAt==null||s.nextSequence<1)throw new IllegalArgumentException("Unsupported snapshot");
        Set<Long> sequences=new HashSet<>();Set<String> activeTeams=new HashSet<>();Set<String> historyEmergencies=new HashSet<>();long max=0;
        for(Map.Entry<String,User> entry:s.users.entrySet()){User u=entry.getValue();if(u==null||!entry.getKey().equals(u.id)||u.role==null||u.name==null)throw new IllegalArgumentException("Invalid user");}
        for(Map.Entry<String,ResponseTeam> entry:s.teams.entrySet()){ResponseTeam t=entry.getValue();if(t==null||!entry.getKey().equals(t.id)||t.type==null||t.getStatus()==null)throw new IllegalArgumentException("Invalid team");coordinates(t.getX(),t.getY());}
        for(Map.Entry<String,Emergency> entry:s.emergencies.entrySet()){
            Emergency e=entry.getValue();if(e==null||!entry.getKey().equals(e.id)||e.type==null||e.getStatus()==null||e.reportedAt==null||e.severity<1||e.severity>5||e.location==null||e.location.isBlank()||e.location.length()>200||!s.users.containsKey(e.reportedBy)||e.sequence<1||!sequences.add(e.sequence))throw new IllegalArgumentException("Invalid emergency");coordinates(e.x,e.y);max=Math.max(max,e.sequence);
            if(e.getStatus()==Status.REPORTED){if(e.getAssignedTeamId()!=null)throw new IllegalArgumentException("Waiting report already has a team");}
            else{ResponseTeam t=s.teams.get(e.getAssignedTeamId());if(t==null||t.type!=e.requiredTeamType())throw new IllegalArgumentException("Invalid assigned team");if(e.getStatus()!=Status.RESOLVED&&(!activeTeams.add(t.id)||t.getStatus()!=TeamStatus.BUSY))throw new IllegalArgumentException("Double-booked or available active team");}
        }
        for(ResponseTeam t:s.teams.values())if((t.getStatus()==TeamStatus.BUSY)!=activeTeams.contains(t.id))throw new IllegalArgumentException("Team status mismatch");
        for(ResponseHistory h:s.history){Emergency e=s.emergencies.get(h.emergencyId);if(e==null||e.getStatus()==Status.REPORTED||!h.teamId.equals(e.getAssignedTeamId())||h.assignedAt==null||!historyEmergencies.add(e.id))throw new IllegalArgumentException("Invalid history");
            if((e.getStatus()==Status.IN_PROGRESS||e.getStatus()==Status.RESOLVED)!=(h.getStartedAt()!=null)||(e.getStatus()==Status.RESOLVED)!=(h.getResolvedAt()!=null))throw new IllegalArgumentException("History lifecycle mismatch");}
        for(Emergency e:s.emergencies.values())if(e.getStatus()!=Status.REPORTED&&!historyEmergencies.contains(e.id))throw new IllegalArgumentException("Missing history");
        if(s.nextSequence<=max)throw new IllegalArgumentException("Sequence counter is stale");
    }
}
