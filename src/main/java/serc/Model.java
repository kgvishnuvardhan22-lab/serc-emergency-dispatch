package serc;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

/** Data-only models. One incident uses one compatible team. */
public final class Model {
    private Model() {}
    public enum EmergencyType { MEDICAL, FIRE, CRIME }
    public enum TeamType { AMBULANCE, FIRE_TRUCK, POLICE }
    public enum Status { REPORTED, ASSIGNED, IN_PROGRESS, RESOLVED }
    public enum TeamStatus { AVAILABLE, BUSY }
    public enum Role { CITIZEN, DISPATCHER, ADMIN }
    public interface Dispatchable { TeamType requiredTeamType(); }
    public static final class User implements Serializable {
        private static final long serialVersionUID=1L;
        public final String id, name, contact;
        public final Role role;
        public User(String id,String name,Role role,String contact) {
            this.id=id; this.name=name; this.role=role; this.contact=contact;
        }
    }
    public abstract static class Emergency implements Serializable, Dispatchable {
        private static final long serialVersionUID=1L;
        public final String id, location, reportedBy;
        public final EmergencyType type;
        public final int severity;
        public final double x,y;
        public final LocalDateTime reportedAt;
        public final long sequence;
        private Status status=Status.REPORTED;
        private String assignedTeamId;
        protected Emergency(String id,EmergencyType type,int severity,double x,double y,
                            String location,String reporter,LocalDateTime time,long sequence) {
            this.id=id;this.type=type;this.severity=severity;this.x=x;this.y=y;
            this.location=location;this.reportedBy=reporter;this.reportedAt=time;this.sequence=sequence;
        }
        public Status getStatus(){return status;}
        public String getAssignedTeamId(){return assignedTeamId;}
        void setState(Status status,String team){this.status=status;this.assignedTeamId=team;}
        public int getSeverity(){return severity;}
        public LocalDateTime getReportedAt(){return reportedAt;}
        public long getSequence(){return sequence;}
        public String toString(){return id+" | "+type+" | severity="+severity+" | "+status+" | "+location+" ("+x+","+y+")";}
    }
    public static final class MedicalEmergency extends Emergency {
        private static final long serialVersionUID=1L;
        MedicalEmergency(String id,int s,double x,double y,String l,String u,LocalDateTime t,long q){super(id,EmergencyType.MEDICAL,s,x,y,l,u,t,q);}
        public TeamType requiredTeamType(){return TeamType.AMBULANCE;}
    }
    public static final class FireEmergency extends Emergency {
        private static final long serialVersionUID=1L;
        FireEmergency(String id,int s,double x,double y,String l,String u,LocalDateTime t,long q){super(id,EmergencyType.FIRE,s,x,y,l,u,t,q);}
        public TeamType requiredTeamType(){return TeamType.FIRE_TRUCK;}
    }
    public static final class CrimeEmergency extends Emergency {
        private static final long serialVersionUID=1L;
        CrimeEmergency(String id,int s,double x,double y,String l,String u,LocalDateTime t,long q){super(id,EmergencyType.CRIME,s,x,y,l,u,t,q);}
        public TeamType requiredTeamType(){return TeamType.POLICE;}
    }
    public static Emergency emergency(String id,EmergencyType type,int severity,double x,double y,String location,String user,LocalDateTime t,long sequence){
        switch(type){
            case MEDICAL:return new MedicalEmergency(id,severity,x,y,location,user,t,sequence);
            case FIRE:return new FireEmergency(id,severity,x,y,location,user,t,sequence);
            case CRIME:return new CrimeEmergency(id,severity,x,y,location,user,t,sequence);
            default:throw new IllegalArgumentException("Unsupported type");
        }
    }
    public static final class ResponseTeam implements Serializable {
        private static final long serialVersionUID=1L;
        public final String id;
        public final TeamType type;
        private TeamStatus status;
        private double x,y;
        public ResponseTeam(String id,TeamType type,double x,double y){this.id=id;this.type=type;this.x=x;this.y=y;this.status=TeamStatus.AVAILABLE;}
        public TeamStatus getStatus(){return status;}
        public double getX(){return x;}
        public double getY(){return y;}
        void setState(TeamStatus status,double x,double y){this.status=status;this.x=x;this.y=y;}
        public String toString(){return id+" | "+type+" | "+status+" | ("+x+","+y+")";}
    }
    public static final class ResponseHistory implements Serializable {
        private static final long serialVersionUID=1L;
        public final String id,emergencyId,teamId;
        public final LocalDateTime assignedAt;
        private LocalDateTime startedAt,resolvedAt;
        ResponseHistory(String id,String e,String t,LocalDateTime at){this.id=id;emergencyId=e;teamId=t;assignedAt=at;}
        public LocalDateTime getStartedAt(){return startedAt;}
        public LocalDateTime getResolvedAt(){return resolvedAt;}
        void setTimes(LocalDateTime start,LocalDateTime end){startedAt=start;resolvedAt=end;}
        public String toString(){return "Incident="+emergencyId+" | team="+teamId+" | assigned="+assignedAt+" | started="+startedAt+" | resolved="+resolvedAt;}
    }
}
