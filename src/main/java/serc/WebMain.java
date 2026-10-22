package serc;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import serc.Model.*;

/** Local browser UI. Deliberately binds only to loopback, not a public network. */
public final class WebMain {
    private static DispatchService service;
    private static String mode;
    private static final int PORT=8765;
    public static void main(String[] args){
        try{run(args);}catch(Exception e){
            System.err.println("SERC could not start: "+e.getMessage());
            System.err.println("For MySQL: start the database, import sql/schema.sql once, check config/mysql.properties and the Connector/J driver. No fallback to file mode was made. See MYSQL_SETUP.md.");
            System.exit(1);
        }
    }
    private static void run(String[] args)throws Exception{
        Set<String> flags=new HashSet<>(Arrays.asList(args));
        Path backup=Paths.get("data/emergency_backup.dat");Store store;Snapshot initial;
        boolean recovery=flags.contains("--recover");
        if(recovery){store=new Store.MemoryStore();initial=Backup.load(backup);mode="READ-ONLY BACKUP RECOVERY";}
        else{
            boolean mysql=flags.contains("--mysql");
            Properties db=new Properties();Path config=Paths.get("config/mysql.properties");
            if(mysql&&Files.exists(config)){try(InputStream in=Files.newInputStream(config)){db.load(in);}}
            store=mysql?new JdbcStore(setting(db,"SERC_DB_URL","url"),setting(db,"SERC_DB_USER","user"),setting(db,"SERC_DB_PASSWORD","password")):new Store.FileStore(Paths.get("data/serc_state.dat"));
            initial=store.load();if(initial.users.isEmpty()&&(flags.contains("--init-demo")||!mysql)){initial=Model.seed();store.save(initial);}
            if(initial.users.isEmpty())throw new IllegalStateException("No users. Start with --mysql --init-demo once.");
            mode=mysql?"MySQL":"Local file demo";
        }
        service=new DispatchService(store,initial,backup,recovery);if(!recovery)service.saveBackup();
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),PORT),0);
        server.createContext("/",WebMain::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(0);try{service.saveBackup();store.close();}catch(Exception e){System.err.println("Close: "+e.getMessage());}}));
        server.start();System.out.println("SERC website: http://127.0.0.1:"+PORT+"\nMode: "+mode+"\nOpen that address in your browser. Keep this terminal open. Ctrl+C stops the server.\nLocal educational simulation only; role selection is not authentication.");
    }
    private static String setting(Properties p,String env,String key){String value=System.getenv(env);return value==null?p.getProperty(key):value;}
    private static void handle(HttpExchange x)throws IOException{
        try{
            String host=x.getRequestHeaders().getFirst("Host");
            if(!("127.0.0.1:"+PORT).equals(host)&&!("localhost:"+PORT).equals(host)){reply(x,403,"application/json","{\"error\":\"Use the local server address.\"}");return;}
            String path=x.getRequestURI().getPath();
            if(path.equals("/")&&x.getRequestMethod().equals("GET")){
                try(InputStream in=WebMain.class.getResourceAsStream("/web/index.html")){
                    if(in==null)throw new IllegalStateException("Web resources missing. Rebuild using build.sh or build.bat.");
                    reply(x,200,"text/html",new String(in.readAllBytes(),StandardCharsets.UTF_8));return;
                }
            }
            if(path.equals("/api/state")&&x.getRequestMethod().equals("GET")){reply(x,200,"application/json",state());return;}
            if(!path.startsWith("/api/")||!x.getRequestMethod().equals("POST")){reply(x,404,"application/json","{\"error\":\"Not found\"}");return;}
            String origin=x.getRequestHeaders().getFirst("Origin");
            if(!("http://127.0.0.1:"+PORT).equals(origin)&&!("http://localhost:"+PORT).equals(origin)){reply(x,403,"application/json","{\"error\":\"Requests must come from this local website.\"}");return;}
            String content=x.getRequestHeaders().getFirst("Content-Type");if(content==null||!content.startsWith("application/x-www-form-urlencoded"))throw new IllegalArgumentException("Use a form request.");
            byte[] body=x.getRequestBody().readNBytes(8193);if(body.length>8192)throw new IllegalArgumentException("Request too large");
            Map<String,String> p=parse(new String(body,StandardCharsets.UTF_8));String result;
            switch(path){
                case "/api/report":result="Report saved: "+service.report(EmergencyType.valueOf(required(p,"type")),Integer.parseInt(required(p,"severity")),Double.parseDouble(required(p,"x")),Double.parseDouble(required(p,"y")),required(p,"location"),required(p,"user"));break;
                case "/api/dispatch":result=service.dispatchNext();break;
                case "/api/start":service.start(required(p,"id"));result="Response started.";break;
                case "/api/resolve":service.resolve(required(p,"id"));result="Resolved. Assigned team is available at the incident location.";break;
                case "/api/team/add":service.addTeam(required(p,"id"),TeamType.valueOf(required(p,"type")),Double.parseDouble(required(p,"x")),Double.parseDouble(required(p,"y")));result="Team added.";break;
                case "/api/team/remove":service.removeTeam(required(p,"id"));result="Unused team removed.";break;
                case "/api/backup":service.saveBackup();result="Backup request complete. Read-only recovery never writes.";break;
                default:reply(x,404,"application/json","{\"error\":\"Not found\"}");return;
            }
            reply(x,200,"application/json","{\"message\":"+quote(result)+",\"warning\":"+quote(service.warning())+"}");
        }catch(IllegalArgumentException|DispatchService.SercException e){reply(x,400,"application/json","{\"error\":"+quote(e.getMessage())+"}");}
        catch(Exception e){e.printStackTrace(System.err);reply(x,500,"application/json","{\"error\":\"Server error. Check the terminal. No success was confirmed.\"}");}
        finally{x.close();}
    }
    private static String required(Map<String,String> p,String key){String v=p.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing "+key);return v.trim();}
    private static Map<String,String> parse(String body){Map<String,String> p=new HashMap<>();if(body.isEmpty())return p;for(String pair:body.split("&")){String[] a=pair.split("=",2);p.put(URLDecoder.decode(a[0],StandardCharsets.UTF_8),a.length==2?URLDecoder.decode(a[1],StandardCharsets.UTF_8):"");}return p;}
    private static void reply(HttpExchange x,int code,String type,String value)throws IOException{
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");x.getResponseHeaders().set("Cache-Control","no-store");x.getResponseHeaders().set("X-Content-Type-Options","nosniff");x.getResponseHeaders().set("X-Frame-Options","DENY");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
        x.sendResponseHeaders(code,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}
    }
    private static String quote(Object value){if(value==null)return "null";String s=value.toString();StringBuilder b=new StringBuilder("\"");for(char c:s.toCharArray()){switch(c){case '\\':b.append("\\\\");break;case '"':b.append("\\\"");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();}
    private static String state(){
        Snapshot s=service.snapshot();StringBuilder b=new StringBuilder("{\"mode\":").append(quote(mode)).append(",\"warning\":").append(quote(service.warning())).append(",\"savedAt\":").append(quote(s.savedAt));
        b.append(",\"users\":[");boolean first=true;for(User u:s.users.values())if(u.role==Role.CITIZEN){if(!first)b.append(',');first=false;b.append("{\"id\":").append(quote(u.id)).append(",\"name\":").append(quote(u.name)).append('}');}
        b.append("],\"teams\":[");first=true;List<ResponseTeam> teams=new ArrayList<>(s.teams.values());teams.sort(Comparator.comparing(t->t.id));for(ResponseTeam t:teams){if(!first)b.append(',');first=false;b.append("{\"id\":").append(quote(t.id)).append(",\"type\":").append(quote(t.type)).append(",\"status\":").append(quote(t.getStatus())).append(",\"x\":").append(t.getX()).append(",\"y\":").append(t.getY()).append('}');}
        b.append("],\"emergencies\":[");first=true;List<Emergency> reports=new ArrayList<>(s.emergencies.values());reports.sort(DispatchService.ORDER);for(Emergency e:reports){if(!first)b.append(',');first=false;b.append("{\"id\":").append(quote(e.id)).append(",\"type\":").append(quote(e.type)).append(",\"severity\":").append(e.severity).append(",\"location\":").append(quote(e.location)).append(",\"user\":").append(quote(e.reportedBy)).append(",\"time\":").append(quote(e.reportedAt)).append(",\"status\":").append(quote(e.getStatus())).append(",\"team\":").append(quote(e.getAssignedTeamId())).append(",\"x\":").append(e.x).append(",\"y\":").append(e.y).append('}');}
        b.append("],\"history\":[");first=true;for(ResponseHistory h:s.history){if(!first)b.append(',');first=false;b.append("{\"id\":").append(quote(h.id)).append(",\"emergencyId\":").append(quote(h.emergencyId)).append(",\"teamId\":").append(quote(h.teamId)).append(",\"assigned\":").append(quote(h.assignedAt)).append(",\"started\":").append(quote(h.getStartedAt())).append(",\"resolved\":").append(quote(h.getResolvedAt())).append('}');}
        return b.append("]}").toString();
    }
}
