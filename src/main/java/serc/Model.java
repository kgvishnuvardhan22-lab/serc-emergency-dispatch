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
}
