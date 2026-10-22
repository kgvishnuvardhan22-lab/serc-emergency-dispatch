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
}
