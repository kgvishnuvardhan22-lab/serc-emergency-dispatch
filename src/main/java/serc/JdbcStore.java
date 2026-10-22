package serc;
import java.sql.*;
import java.time.LocalDateTime;
import serc.Model.*;

/** Small teaching implementation: replace the full snapshot in one transaction.
 * Not designed for large datasets. Revision checks reject competing app instances. */
public final class JdbcStore implements Store {
    public static final class UncertainCommitException extends Exception {
        private static final long serialVersionUID=1L;
        UncertainCommitException(Throwable cause){super("Commit outcome unknown. Restart and inspect the database before retrying.",cause);}
    }
    private final String url,user,password;
    private long revision=-1;
    public JdbcStore(String url,String user,String password){
        if(url==null||user==null||password==null)throw new IllegalArgumentException("Set SERC_DB_URL, SERC_DB_USER and SERC_DB_PASSWORD.");
        this.url=url;this.user=user;this.password=password;
    }
    private Connection open()throws SQLException{return DriverManager.getConnection(url,user,password);}
    private static LocalDateTime time(ResultSet r,String field)throws SQLException{Timestamp t=r.getTimestamp(field);return t==null?null:t.toLocalDateTime();}
    private static void timestamp(PreparedStatement p,int index,LocalDateTime value)throws SQLException{if(value==null)p.setNull(index,Types.TIMESTAMP);else p.setTimestamp(index,Timestamp.valueOf(value));}
    public Snapshot load()throws SQLException{
        try(Connection c=open()){
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
            Snapshot s=new Snapshot();long loadedRevision;
            try(Statement st=c.createStatement()){
                try(ResultSet r=st.executeQuery("SELECT * FROM app_metadata WHERE id=1")){if(!r.next())throw new SQLException("Missing app_metadata row. Run schema.sql.");loadedRevision=r.getLong("revision");s.nextSequence=r.getLong("next_sequence");s.savedAt=time(r,"saved_at");}
                try(ResultSet r=st.executeQuery("SELECT * FROM users")){while(r.next()){User u=new User(r.getString("user_id"),r.getString("name"),Role.valueOf(r.getString("role")),r.getString("contact"));s.users.put(u.id,u);}}
                try(ResultSet r=st.executeQuery("SELECT * FROM response_teams")){while(r.next()){ResponseTeam t=new ResponseTeam(r.getString("team_id"),TeamType.valueOf(r.getString("type")),r.getDouble("x_coord"),r.getDouble("y_coord"));t.setState(TeamStatus.valueOf(r.getString("status")),t.getX(),t.getY());s.teams.put(t.id,t);}}
                try(ResultSet r=st.executeQuery("SELECT * FROM emergencies")){while(r.next()){Emergency e=Model.emergency(r.getString("emergency_id"),EmergencyType.valueOf(r.getString("type")),r.getInt("severity"),r.getDouble("x_coord"),r.getDouble("y_coord"),r.getString("location"),r.getString("reported_by"),time(r,"reported_at"),r.getLong("queue_sequence"));e.setState(Status.valueOf(r.getString("status")),r.getString("assigned_team_id"));s.emergencies.put(e.id,e);}}
                try(ResultSet r=st.executeQuery("SELECT * FROM response_history ORDER BY assigned_at,history_id")){while(r.next()){ResponseHistory h=new ResponseHistory(r.getString("history_id"),r.getString("emergency_id"),r.getString("team_id"),time(r,"assigned_at"));h.setTimes(time(r,"started_at"),time(r,"resolved_at"));s.history.add(h);}}
                DispatchService.validate(s);c.commit();revision=loadedRevision;return s;
            }catch(SQLException|RuntimeException ex){c.rollback();throw ex;}
        }
    }
    public void save(Snapshot s)throws Exception{
        DispatchService.validate(s);
        try(Connection c=open()){
            c.setAutoCommit(false);boolean commitAttempted=false;
            try{
                try(PreparedStatement p=c.prepareStatement("SELECT revision FROM app_metadata WHERE id=1 FOR UPDATE");ResultSet r=p.executeQuery()){
                    if(!r.next()||revision<0||r.getLong(1)!=revision)throw new SQLException("Database changed in another session. Restart to reload; write rejected.");
                }
                // Deletes and inserts roll back as a unit. Do not use TRUNCATE here.
                try(Statement st=c.createStatement()){
                    st.executeUpdate("DELETE FROM response_history");st.executeUpdate("DELETE FROM emergencies");st.executeUpdate("DELETE FROM response_teams");st.executeUpdate("DELETE FROM users");
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO users(user_id,name,role,contact) VALUES (?,?,?,?)")){
                    for(User u:s.users.values()){p.setString(1,u.id);p.setString(2,u.name);p.setString(3,u.role.name());p.setString(4,u.contact);p.addBatch();}p.executeBatch();
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO response_teams(team_id,type,status,x_coord,y_coord) VALUES (?,?,?,?,?)")){
                    for(ResponseTeam t:s.teams.values()){p.setString(1,t.id);p.setString(2,t.type.name());p.setString(3,t.getStatus().name());p.setDouble(4,t.getX());p.setDouble(5,t.getY());p.addBatch();}p.executeBatch();
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO emergencies(emergency_id,type,severity,location,x_coord,y_coord,reported_by,reported_at,queue_sequence,status,assigned_team_id) VALUES (?,?,?,?,?,?,?,?,?,?,?)")){
                    for(Emergency e:s.emergencies.values()){p.setString(1,e.id);p.setString(2,e.type.name());p.setInt(3,e.severity);p.setString(4,e.location);p.setDouble(5,e.x);p.setDouble(6,e.y);p.setString(7,e.reportedBy);timestamp(p,8,e.reportedAt);p.setLong(9,e.sequence);p.setString(10,e.getStatus().name());p.setString(11,e.getAssignedTeamId());p.addBatch();}p.executeBatch();
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO response_history(history_id,emergency_id,team_id,assigned_at,started_at,resolved_at) VALUES (?,?,?,?,?,?)")){
                    for(ResponseHistory h:s.history){p.setString(1,h.id);p.setString(2,h.emergencyId);p.setString(3,h.teamId);timestamp(p,4,h.assignedAt);timestamp(p,5,h.getStartedAt());timestamp(p,6,h.getResolvedAt());p.addBatch();}p.executeBatch();
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE app_metadata SET revision=?,next_sequence=?,saved_at=? WHERE id=1")){p.setLong(1,revision+1);p.setLong(2,s.nextSequence);timestamp(p,3,s.savedAt);if(p.executeUpdate()!=1)throw new SQLException("Metadata update failed");}
                commitAttempted=true;c.commit();revision++;
            }catch(Exception ex){try{c.rollback();}catch(SQLException rollback){ex.addSuppressed(rollback);}if(commitAttempted)throw new UncertainCommitException(ex);throw ex;}
        }
    }
}
