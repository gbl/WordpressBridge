package de.guntram.bukkit.WordpressBridge;

// import java.sql.Connection;
import com.mysql.jdbc.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import static org.bukkit.craftbukkit.libs.org.apache.commons.codec.digest.DigestUtils.md5Hex;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class WPBPlugin extends JavaPlugin {

    private FileConfiguration config;
    private Logger logger;

    private String host, user, password, database, prefix, capabilities;
    private int port;
    
    private Connection mysql;
    
    @Override
    public void onEnable() {
        logger=getLogger();
        saveDefaultConfig();
        config=getConfig();
        
        host=config.getString("host", "localhost");
        port=config.getInt("port", 3306);
        user=config.getString("user", "wordpress");
        password=config.getString("password", "");
        database=config.getString("database", "wordpress");
        prefix=config.getString("prefix", "wp_");
        capabilities=config.getString("capabilities", "a:1:{s:15:\"bbp_participant\";b:1;}");
        
        mysql=null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            mysql = (Connection) DriverManager.getConnection("jdbc:mysql://"+host+":"+port+
                    "/"+database+"?user="+user+"&password="+password);
        } catch (ClassNotFoundException ex) {
            logger.log(Level.SEVERE, "mysql driver not found", ex);
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "cannot establish connection", ex);
            logger.log(Level.SEVERE, "this happened trying to connect to mysql "+
                                    "on {0} port {1} using database name {2} username {3}", 
                    new Object[]{host, port, database, user});
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This can only be done by players");
            return false;
        }
        if (mysql==null) {
            sender.sendMessage("There was a problem connecting to the database, you can't use that command now. Contact staff.");
            return true;
        }
        String commandName=cmd.getName();
        if (!(commandName.equals("webpassword")))
            return false;
        if (args.length!=1) {
            sender.sendMessage("You need to type your new password, which must not include spaces, behind /webpassword");
            return false;
        }
        if (createUserOrSetPassword(sender.getName(), ((Player)sender).getUniqueId().toString(), args[0])) {
            sender.sendMessage("Your password was set");
        } else {
            sender.sendMessage("There was a problem, contact staff");
        }
        return false;
    }


    private boolean createUserOrSetPassword(String name, String uuid, String password) {
        Statement query;
        try {
            query=mysql.createStatement();
            ResultSet result=query.executeQuery("select id from wp_users where user_login='"+name+"'");
            if (result.next()) {
                // user already exists
                int id=result.getInt("id");
                String q="select meta_value from wp_usermeta where user_id="+id+" and meta_key='minecraftuuid'";
                logger.log(Level.INFO, q);
                ResultSet uuidres=query.executeQuery(q);
                if (uuidres.next()) {
                    String dbuuid=uuidres.getString("meta_value");
                    if (!(dbuuid.equals(uuid))) {
                        logger.log(Level.WARNING, "User "+name+"trying to set password for uuid"+dbuuid+", but own uuid is"+uuid);
                        return false;
                    }
                } else {
                    query.executeQuery("insert into wp_usermeta (umeta_id, user_id, meta_key, meta_value)"+
                            "values (null, "+id+", 'minecraftuuid', '"+uuid+"'");
                }
                query.execute("update wp_users set user_pass='"+md5Hex(password)+"' where id="+id);
                return true;
            } else {
                String q="insert into wp_users("+
                                "id, user_login, user_pass, user_nicename, user_email, user_url, user_registered, user_activation_key, user_status, display_name"+
                                ") values ("+
                                "null, '"+name+"', '"+md5Hex(password)+"', '"+name+"', 'unknown@email', '', now(), '', 0, '"+name+"')";
                logger.log(Level.INFO, q);
                query.executeUpdate(q, Statement.RETURN_GENERATED_KEYS);
                result=query.getGeneratedKeys();
                result.next();
                int id=result.getInt(1);
                PreparedStatement pstmt=mysql.prepareStatement(""+
                        "insert into wp_usermeta (umeta_id, user_id, meta_key, meta_value)"+
                        "values (null, ?, ?, ?)"+
                "");
                pstmt.setInt(1, id); pstmt.setString(2, "minecraftuuid");       pstmt.setString(3, uuid);       pstmt.executeUpdate();
                pstmt.setInt(1, id); pstmt.setString(2, "nickname");            pstmt.setString(3, name);       pstmt.executeUpdate();
                pstmt.setInt(1, id); pstmt.setString(2, "wp_user_level");       pstmt.setString(3, "0");        pstmt.executeUpdate();
                pstmt.setInt(1, id); pstmt.setString(2, "show_admin_bar_front");pstmt.setString(3, "false");    pstmt.executeUpdate();
                pstmt.setInt(1, id); pstmt.setString(2, "wp_capabilities");     pstmt.setString(3, capabilities);  pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, null, ex);
            return false;
        }
    }
}
